package consulo.turtlecommander.ui;

import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.component.ProcessCanceledException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.service.VfsTempCleanup;
import consulo.turtlecommander.vfs.OpenVfsRegistry;
import consulo.turtlecommander.vfs.VfsPaths;
import org.jspecify.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * The payload of a drag out of a panel. Drops inside the plugin take the entry list directly;
 * external targets get real files, extracting archive entries on demand.
 */
public class FileEntryTransferable implements Transferable {
    /** In-JVM flavour carrying the {@link FileEntry} list untouched, so no extraction is needed. */
    public static final DataFlavor FILE_ENTRY_FLAVOR = new DataFlavor(
        DataFlavor.javaJVMLocalObjectMimeType + ";class=java.util.List", "FileEntry List");

    private static final DataFlavor[] SUPPORTED_FLAVORS = {FILE_ENTRY_FLAVOR, DataFlavor.javaFileListFlavor};

    private final List<FileEntry> myEntries;
    private final @Nullable Project myProject;

    /**
     * Cached {@link DataFlavor#javaFileListFlavor} payload. Drop targets may request the same
     * flavour several times during one drop, and archive-backed entries must not be re-extracted
     * each time. A cancelled extraction is not cached, so a later request starts over.
     */
    private @Nullable List<File> myExportedFiles;

    public FileEntryTransferable(List<FileEntry> entries, @Nullable Project project) {
        myEntries = entries;
        myProject = project;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return SUPPORTED_FLAVORS.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        for (DataFlavor supported : SUPPORTED_FLAVORS) {
            if (supported.equals(flavor)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (FILE_ENTRY_FLAVOR.equals(flavor)) {
            return myEntries;
        }
        if (DataFlavor.javaFileListFlavor.equals(flavor)) {
            if (myExportedFiles == null) {
                myExportedFiles = exportFilesWithProgress();
            }
            return myExportedFiles;
        }
        throw new UnsupportedFlavorException(flavor);
    }

    /**
     * Runs the export under a cancellable modal progress when that is both needed and possible.
     * Needed only when some entry lives on a non-default filesystem, so real extraction work is
     * coming. Possible only on the UI thread — same-JVM drop targets (the project tree, the editor)
     * request the flavour there, and a synchronous progress dialog is exactly what keeps the UI
     * painting while the work runs elsewhere. Requests from external applications arrive on the AWT
     * toolkit thread instead, where the UI thread is free anyway.
     *
     * <p>Cancelling surfaces as {@link IOException}, the failure mode {@link #getTransferData}
     * defines; the drop target treats the drop as failed and any partial extraction is left to the
     * usual delete-on-exit and stale-sweep cleanup.
     */
    private List<File> exportFilesWithProgress() throws IOException {
        boolean needsExtraction = false;
        for (FileEntry entry : myEntries) {
            if (entry.path().getFileSystem() != FileSystems.getDefault()) {
                needsExtraction = true;
                break;
            }
        }

        Application application = Application.get();
        if (!needsExtraction || !application.isDispatchThread()) {
            return exportFiles(null);
        }
        try {
            return ProgressManager.getInstance().<List<File>, IOException>runProcessWithProgressSynchronously(
                () -> exportFiles(ProgressManager.getInstance().getProgressIndicator()),
                LocalizeValue.localizeTODO("Extracting files from archive"),
                true,
                myProject);
        }
        catch (ProcessCanceledException e) {
            throw new IOException("Extraction cancelled");
        }
    }

    /**
     * Builds the real-file list an external drop target receives. Plain local files and
     * temp-dir-backed archive entries already live on the default filesystem — they only need lazy
     * stubs streamed before the target copies them. Entries from a non-default filesystem have no
     * on-disk form at all ({@code Path.toFile()} would throw), so they are extracted into a fresh
     * temp directory and the copies handed out instead.
     *
     * <p>Runs at drop time, so only accepted drops pay the extraction cost.
     */
    private List<File> exportFiles(@Nullable ProgressIndicator indicator) throws IOException {
        ExportProgress progress = new ExportProgress(indicator, countExportSteps());
        List<File> files = new ArrayList<>(myEntries.size());
        Path tempDir = null;

        for (FileEntry entry : myEntries) {
            if (entry.path().getFileSystem() == FileSystems.getDefault()) {
                progress.step(entry.name());
                OpenVfsRegistry.materializeTreeIfNeeded(entry.path());
                files.add(entry.path().toFile());
            }
            else {
                if (tempDir == null) {
                    tempDir = createDndTempDir();
                }
                files.add(extractEntryForExport(entry, tempDir, progress));
            }
        }
        return files;
    }

    /**
     * How many {@link ExportProgress#step} calls the export will make: one per file to copy, with
     * archive directories pre-walked to count theirs. The pre-walk is cheap — an archive filesystem
     * answers it from its in-memory directory.
     */
    private int countExportSteps() throws IOException {
        int[] count = {0};
        for (FileEntry entry : myEntries) {
            if (entry.path().getFileSystem() != FileSystems.getDefault()
                && Files.isDirectory(entry.path())) {
                Files.walkFileTree(entry.path(), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        count[0]++;
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            else {
                count[0]++;
            }
        }
        return count[0];
    }

    /**
     * Temp directory holding archive entries extracted for one drag-out. Deletion follows the same
     * two-tier scheme as the other {@code turtle-*} temp dirs: delete-on-exit for a normal JVM
     * shutdown, by which time the drop target's copy has long finished, plus the stale sweep as the
     * crash safety net. It can't be deleted eagerly because nothing tells us when the drop target
     * has finished reading.
     */
    private static Path createDndTempDir() throws IOException {
        VfsTempCleanup.cleanupOnce();
        Path dir = Files.createTempDirectory("turtle-dnd-");
        dir.toFile().deleteOnExit();
        return dir;
    }

    private static File extractEntryForExport(FileEntry entry, Path tempDir, ExportProgress progress)
        throws IOException {
        Path target = VfsPaths.resolveEntryPath(tempDir, entry.name());
        if (target == null) {
            throw new IOException("Cannot export archive entry: " + entry.name());
        }
        if (Files.isDirectory(entry.path())) {
            copyTreeForExport(entry.path(), target, progress);
        }
        else {
            progress.step(entry.name());
            Files.copy(entry.path(), target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();
        }
        return target.toFile();
    }

    /**
     * Recursive copy from an archive filesystem into the export temp dir. Parents are registered
     * for delete-on-exit before their children — deletion runs in reverse registration order, so
     * files go first and directories are empty by the time their turn comes. Relative names go
     * through the same containment check as a real extraction, so hostile entry names can't escape
     * the temp dir and characters illegal on the host OS are sanitised.
     */
    private static void copyTreeForExport(Path source, Path target, ExportProgress progress)
        throws IOException {
        Files.createDirectories(target);
        target.toFile().deleteOnExit();

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(source)) {
                    return FileVisitResult.CONTINUE;
                }
                Path resolved = resolveChild(dir);
                if (resolved == null) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(resolved);
                resolved.toFile().deleteOnExit();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path resolved = resolveChild(file);
                if (resolved != null) {
                    progress.step(file.getFileName().toString());
                    Files.copy(file, resolved, StandardCopyOption.REPLACE_EXISTING);
                    resolved.toFile().deleteOnExit();
                }
                return FileVisitResult.CONTINUE;
            }

            private @Nullable Path resolveChild(Path path) {
                return VfsPaths.resolveEntryPath(target, source.relativize(path).toString());
            }
        });
    }

    /**
     * Per-file progress for the export. Every call is a no-op when the export runs without an
     * indicator — small selections, off-UI-thread requests, tests.
     */
    private static final class ExportProgress {
        private final @Nullable ProgressIndicator myIndicator;
        private final int myTotal;
        private int myDone;

        ExportProgress(@Nullable ProgressIndicator indicator, int total) {
            myIndicator = indicator;
            myTotal = total;
        }

        void step(String name) {
            if (myIndicator == null) {
                return;
            }
            myIndicator.checkCanceled();
            myIndicator.setText2(LocalizeValue.of(name));
            myDone++;
            if (myTotal > 0) {
                myIndicator.setIndeterminate(false);
                myIndicator.setFraction((double) myDone / myTotal);
            }
        }
    }
}
