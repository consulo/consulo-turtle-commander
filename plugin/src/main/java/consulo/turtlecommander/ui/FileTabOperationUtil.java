package consulo.turtlecommander.ui;

import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.fileEditor.FileEditorManager;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.dialog.CombineFilesDialogDescriptor;
import consulo.turtlecommander.dialog.CreateLinkDialogDescriptor;
import consulo.turtlecommander.dialog.DeleteDialogDescriptor;
import consulo.turtlecommander.dialog.ExtractDialogDescriptor;
import consulo.turtlecommander.dialog.MultiRenameDialogDescriptor;
import consulo.turtlecommander.dialog.PackDialogDescriptor;
import consulo.turtlecommander.dialog.SplitFileDialogDescriptor;
import consulo.turtlecommander.dialog.TransferDialogDescriptor;
import consulo.turtlecommander.filetype.TurtleCommanderNotifications;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.operation.ArchiveFormat;
import consulo.turtlecommander.operation.CombineFilesOperation;
import consulo.turtlecommander.operation.SplitFileOperation;
import consulo.turtlecommander.service.ArchiveService;
import consulo.turtlecommander.service.ErrorListener;
import consulo.turtlecommander.service.FileOperationService;
import consulo.turtlecommander.service.OverwriteConfirmation;
import consulo.turtlecommander.service.OverwritePolicy;
import consulo.turtlecommander.service.OverwriteResponse;
import consulo.turtlecommander.service.ProgressListener;
import consulo.turtlecommander.service.VfsTempCleanup;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.util.BackgroundProgress;
import consulo.turtlecommander.util.FileUtils;
import consulo.turtlecommander.util.UiTask;
import consulo.turtlecommander.vfs.OpenVfsRegistry;
import consulo.turtlecommander.vfs.VfsEditEntry;
import consulo.turtlecommander.vfs.VfsEditService;
import consulo.turtlecommander.vfs.VfsOpenProgress;
import consulo.turtlecommander.vfs.VfsPaths;
import consulo.turtlecommander.vfs.VfsStackEntry;
import consulo.turtlecommander.vfs.VirtualFileSystem;
import consulo.turtlecommander.vfs.VirtualFileSystemRegistry;
import consulo.turtlecommander.vfs.ZipVirtualFileSystem;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.InputBoxBuilder;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.awt.Desktop;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import consulo.ui.Alert;
import consulo.ui.ex.dialog.DialogDescriptor;
import java.util.concurrent.CompletableFuture;
import java.io.IOException;
import consulo.ui.annotation.RequiredUIAccess;

/**
 * The file operations a panel can start: copy, move, delete, link, pack, extract, split, combine,
 * rename and open.
 *
 * <p>Every long operation follows the same shape — a cancellable background progress whose indicator
 * drives the per-file callbacks, per-entry failures surfaced as notifications rather than aborting
 * the batch, and a refresh of both panels at the end. Cancel is observed by polling
 * {@link ProgressIndicator#isCanceled()}, so an operation finishes its in-flight file and still runs
 * its cache invalidation and refresh rather than being killed at an arbitrary point.
 */
public final class FileTabOperationUtil {
    private FileTabOperationUtil() {
    }

    // --- Copy and move ---------------------------------------------------------------------------------

    public static void performCopy(FileManagerTab tab) {
        Path destination = transferDestination(tab, "copy");
        if (destination != null) {
            performCopyEntries(tab, tab.getSelectedEntries(), destination, null);
        }
    }

    public static void performMove(FileManagerTab tab) {
        Path destination = transferDestination(tab, "move");
        if (destination != null) {
            performMoveEntries(tab, tab.getSelectedEntries(), destination, null, null);
        }
    }

    /**
     * The other panel's directory. Inside an archive that is the archive-internal path rather than
     * the real filesystem one, so entries land inside the archive as the user expects.
     */
    private static @Nullable Path transferDestination(FileManagerTab tab, String verb) {
        FileManagerTab otherTab = tab.getOtherPanelTab();
        VirtualFileSystem otherVfs = otherTab != null ? otherTab.getCurrentVfs() : null;
        if (otherVfs != null && otherVfs.isReadOnly()) {
            tab.fileErrorNotification("Cannot " + verb + " into a read-only archive");
            return null;
        }
        if (otherTab != null && otherTab.isInsideArchive()) {
            // an archive opened through the platform's archive file systems has no file system of its own here,
            // and those are read only
            if (otherVfs == null) {
                tab.fileErrorNotification("Cannot " + verb + " into a read-only archive");
                return null;
            }
            return otherTab.getCurrentPath();
        }
        return tab.getOtherPanelPath();
    }

    public static void performCopyEntries(FileManagerTab tab, List<FileEntry> selected, Path destination,
                                          @Nullable String destinationDisplayPath) {
        if (selected.isEmpty()) {
            return;
        }
        String displayPath = resolveDisplayPath(tab, destination, destinationDisplayPath);
        TransferDialogDescriptor descriptor = new TransferDialogDescriptor("Copy", selected, destination, displayPath);
        showDialog(descriptor, () -> runTransfer(tab, sourcePaths(selected), descriptor.getTargetDirectory(),
            descriptor.getPolicy(), "Copying files", "Copying", "copy",
            tab.getFileOps()::copyFilesWithProgress, null));
    }

    public static void performMoveEntries(FileManagerTab tab, List<FileEntry> selected, Path destination,
                                          @Nullable String destinationDisplayPath,
                                          @Nullable Runnable onTransferFinished) {
        if (selected.isEmpty()) {
            return;
        }
        String displayPath = resolveDisplayPath(tab, destination, destinationDisplayPath);
        TransferDialogDescriptor descriptor = new TransferDialogDescriptor("Move", selected, destination, displayPath);
        showDialog(descriptor, () -> runTransfer(tab, sourcePaths(selected), descriptor.getTargetDirectory(),
            descriptor.getPolicy(), "Moving files", "Moving", "move",
            tab.getFileOps()::moveFilesWithProgress, onTransferFinished));
    }

    private static String resolveDisplayPath(FileManagerTab tab, Path destination, @Nullable String provided) {
        if (provided != null) {
            return provided;
        }
        String otherDisplay = tab.getOtherPanelDisplayPath();
        return otherDisplay != null ? otherDisplay : destination.toString();
    }

    /**
     * What {@code copyFilesWithProgress} and {@code moveFilesWithProgress} have in common — the
     * only difference between copy and move at this layer is which one gets called.
     */
    @FunctionalInterface
    private interface TransferOp {
        void run(List<Path> sources, Path destination, OverwritePolicy initialPolicy,
                 ProgressListener onProgress, OverwriteConfirmation onOverwriteConfirm,
                 ErrorListener onError, BooleanSupplier isCancelled);
    }

    /**
     * Shared driver for copy and move: count the files, run the operation with a progress-bound
     * callback, wire the per-file overwrite prompt, surface errors as notifications, and refresh
     * both panels afterwards.
     *
     * @param onTransferFinished runs after the operation returns — normally, after per-file errors,
     *                           or after a cancel — but never when the dialog was dismissed, since
     *                           then no transfer started. Used by cut-paste to settle its buffer
     *                           against the actual outcome.
     */
    private static void runTransfer(FileManagerTab tab, List<Path> sources, Path destination,
                                    OverwritePolicy initialPolicy, String taskTitle, String progressVerb,
                                    String errorVerb, TransferOp op,
                                    @Nullable Runnable onTransferFinished) {
        BackgroundProgress.run(tab.getProject(), LocalizeValue.localizeTODO(taskTitle), indicator -> {
            // checked here rather than in the dialog: on a dead network destination the stat blocks for the SMB
            // timeout
            indicator.setText(LocalizeValue.localizeTODO("Checking destination..."));
            String destinationError = prepareDestination(destination);
            if (destinationError != null) {
                notifyError(tab, destinationError);
                return;
            }

            indicator.setText(LocalizeValue.localizeTODO("Counting files..."));
            int totalFiles = FileUtils.countFiles(sources);
            indicator.setIndeterminate(false);

            op.run(sources, destination, initialPolicy,
                countingProgress(indicator, progressVerb, totalFiles),
                path -> askOverwriteConfirm(tab, path),
                errorNotifier(tab, "Failed to " + errorVerb),
                indicator::isCanceled);

            if (onTransferFinished != null) {
                onTransferFinished.run();
            }
            finishAndRefresh(tab);
        });
    }

    // --- Delete ------------------------------------------------------------------------------------------

    public static void performDelete(FileManagerTab tab, boolean forcePermanent) {
        List<FileEntry> selected = tab.getSelectedEntries();
        if (selected.isEmpty()) {
            return;
        }

        // Shift+Delete always deletes irreversibly, bypassing the recycle-bin setting. Otherwise
        // honour the preference — but only when the platform actually has a trash, so the user
        // knowingly confirms a permanent delete instead of silently getting one.
        boolean wantsRecycleBin = !forcePermanent
            && TurtleCommanderSettings.getInstance().getState().deleteToRecycleBin;
        boolean useRecycleBin = wantsRecycleBin && tab.getFileOps().isRecycleBinAvailable();
        boolean downgraded = wantsRecycleBin && !useRecycleBin;

        DeleteDialogDescriptor descriptor = new DeleteDialogDescriptor(selected, useRecycleBin,
            downgraded ? "Recycle Bin is not available on this system \u2014 items will be deleted permanently." : null,
            downgraded ? "Turn off \"Delete to Recycle Bin\" and don't show this note again" : null);
        showDialog(descriptor, () -> {
            if (downgraded && descriptor.isCheckboxSelected()) {
                TurtleCommanderSettings.getInstance().getState().deleteToRecycleBin = false;
            }
            delete(tab, selected, useRecycleBin);
        });
    }

    /** Shows a unified dialog and runs {@code onOk} when it closes with OK - a cancelled one completes exceptionally. */
    @RequiredUIAccess
    private static void showDialog(DialogDescriptor descriptor, Runnable onOk) {
        Application.get().getInstance(DialogService.class).build(descriptor).showAsync().whenComplete((value, error) -> {
            if (error == null) {
                onOk.run();
            }
        });
    }

    /** Returns an error message, or {@code null} when the destination is a directory or could be made one. */
    private static @Nullable String prepareDestination(Path destination) {
        if (Files.exists(destination)) {
            return Files.isDirectory(destination) ? null : "Destination is not a directory:\n" + destination;
        }
        try {
            Files.createDirectories(destination);
            return null;
        }
        catch (IOException e) {
            return "Failed to create destination directory:\n" + e.getMessage();
        }
    }

    private static void delete(FileManagerTab tab, List<FileEntry> selected, boolean useRecycleBin) {
        List<Path> sourcePaths = sourcePaths(selected);
        String taskTitle = useRecycleBin ? "Moving files to Recycle Bin" : "Deleting files";
        String progressVerb = useRecycleBin ? "Moving" : "Deleting";

        BackgroundProgress.run(tab.getProject(), LocalizeValue.localizeTODO(taskTitle), indicator -> {
            // Recycle-bin mode hands each top-level path to the OS in one call and ticks once per
            // path, so the denominator is the path count — a recursive total would leave the bar
            // stuck near zero and the tree walk would be wasted work.
            int totalFiles;
            if (useRecycleBin) {
                totalFiles = sourcePaths.size();
            }
            else {
                indicator.setText(LocalizeValue.localizeTODO("Counting files..."));
                totalFiles = FileUtils.countFiles(sourcePaths);
            }
            indicator.setIndeterminate(false);

            tab.getFileOps().deleteFilesWithProgress(sourcePaths,
                countingProgress(indicator, progressVerb, totalFiles),
                errorNotifier(tab, "Failed to delete"),
                indicator::isCanceled,
                useRecycleBin);

            finishAndRefresh(tab);
        });
    }

    // --- Create ------------------------------------------------------------------------------------------

    public static void performCreateDirectory(FileManagerTab tab) {
        askName(LocalizeValue.localizeTODO("New Directory"), LocalizeValue.localizeTODO("Enter directory name:"),
            name -> createDirectory(tab, name));
    }

    /** Asks for a name in the unified prompt, which every frontend shows; a blank or cancelled answer does nothing. */
    private static void askName(LocalizeValue title, LocalizeValue label, Consumer<String> onName) {
        InputBoxBuilder.text()
            .title(title)
            .text(label)
            .showAsync()
            .whenComplete((name, error) -> {
                if (error == null && name != null && !name.isBlank()) {
                    onName.accept(name);
                }
            });
    }

    private static void createDirectory(FileManagerTab tab, String name) {
        UiTask.background(() -> {
            try {
                tab.getFileOps().createDirectory(tab.getCurrentPath(), name);
                if (tab.getCurrentVfs() != null) {
                    refreshAfterVfsChange(tab, name);
                }
                else {
                    tab.invalidateFreeSpaceCache();
                    tab.navigateTo(tab.getCurrentPath(), name);
                }
                tab.getUIAccess().give(tab::refreshOtherPanel);
            }
            catch (Exception e) {
                notifyError(tab, "Create directory failed: " + FileUtils.fileErrorMessage(e));
            }
        });
    }

    public static void performCreateFile(FileManagerTab tab) {
        askName(LocalizeValue.localizeTODO("New File"), LocalizeValue.localizeTODO("Enter file name:"),
            name -> createFile(tab, name));
    }

    private static void createFile(FileManagerTab tab, String name) {
        UiTask.background(() -> {
            try {
                Path filePath = Files.createFile(tab.getCurrentPath().resolve(name));
                tab.getFileOps().invalidateListingCache(tab.getCurrentPath());
                if (tab.getCurrentVfs() != null) {
                    refreshAfterVfsChange(tab, name);
                }
                else {
                    tab.invalidateFreeSpaceCache();
                    tab.navigateTo(tab.getCurrentPath(), name);
                }
                tab.getUIAccess().give(tab::refreshOtherPanel);

                // Inside an archive the refresh may have repacked and re-rooted the filesystem, so
                // the entry is resolved against the current path rather than the pre-refresh one.
                Path entryPath = tab.getCurrentVfs() != null ? tab.getCurrentPath().resolve(name) : filePath;
                FileEntry entry = FileEntry.builder(name, entryPath).build();
                tab.getUIAccess().give(() -> openFile(tab, entry));
            }
            catch (Exception e) {
                notifyError(tab, "Create file failed: " + FileUtils.fileErrorMessage(e));
            }
        });
    }

    /**
     * Dual-pane link creation: the active panel's selection becomes symbolic or hard links in the
     * opposite panel's directory, the orthodox "link in other panel" idiom. Links are created off
     * the UI thread; the other panel is then refreshed with the new link selected, without stealing
     * focus from the active panel.
     */
    public static void performCreateLink(FileManagerTab tab) {
        if (tab.isInsideArchive()) {
            tab.fileErrorNotification("Cannot create symbolic links inside an archive");
            return;
        }
        List<FileEntry> selected = withoutParentLink(tab.getSelectedEntries());
        if (selected.isEmpty()) {
            return;
        }
        FileManagerTab otherTab = tab.getOtherPanelTab();
        if (otherTab != null && otherTab.getCurrentVfs() != null) {
            tab.fileErrorNotification("Cannot create symbolic links inside an archive");
            return;
        }
        Path destination = tab.getOtherPanelPath();
        if (destination == null) {
            return;
        }
        String displayPath = resolveDisplayPath(tab, destination, null);

        CreateLinkDialogDescriptor dialog = new CreateLinkDialogDescriptor(selected, destination, displayPath);
        // its own showAsync: it checks the link paths off the UI thread and closes itself once they pass
        dialog.showAsync().whenComplete((value, error) -> {
            List<CreateLinkDialogDescriptor.LinkSpec> specs = error == null ? dialog.getSpecs() : List.of();
            if (!specs.isEmpty()) {
                createLinks(tab, specs);
            }
        });
    }

    private static void createLinks(FileManagerTab tab, List<CreateLinkDialogDescriptor.LinkSpec> specs) {
        UiTask.background(() -> {
            int failures = 0;
            for (CreateLinkDialogDescriptor.LinkSpec spec : specs) {
                try {
                    if (spec.hard()) {
                        tab.getFileOps().createHardLink(spec.link(), spec.target());
                    }
                    else {
                        tab.getFileOps().createSymbolicLink(spec.link(), spec.target());
                    }
                }
                catch (Exception e) {
                    failures++;
                    notifyError(tab, linkErrorMessage(spec.link(), spec.hard(), e));
                }
            }
            if (failures == specs.size()) {
                return;
            }
            FileManagerTab target = tab.getOtherPanelTab();
            if (target == null) {
                tab.getUIAccess().give(tab::refreshOtherPanel);
                return;
            }
            Path linkName = specs.size() == 1 ? specs.get(0).link().getFileName() : null;
            target.navigateTo(target.getCurrentPath(), linkName != null ? linkName.toString() : null);
        });
    }

    /**
     * Windows refuses symbolic-link creation without Developer Mode or elevation, and the raw
     * error says only "a required privilege is not held" — useless without the context.
     */
    private static String linkErrorMessage(Path link, boolean hard, Exception e) {
        boolean privilege = e instanceof AccessDeniedException
            || (e.getMessage() != null && StringUtil.containsIgnoreCase(e.getMessage(), "privilege"));
        if (privilege && !hard) {
            return "Creating symbolic links requires Windows Developer Mode or administrator rights.";
        }
        return "Failed to create link " + link.getFileName() + ": " + FileUtils.fileErrorMessage(e);
    }

    // --- Split and combine --------------------------------------------------------------------------------

    public static void performSplitFile(FileManagerTab tab) {
        if (tab.isInsideArchive()) {
            return;
        }
        FileEntry entry = tab.getSelectedEntry();
        if (entry == null || entry.directory() || entry.parentLink()) {
            return;
        }

        Path otherPath = tab.getOtherPanelPath();
        SplitFileDialogDescriptor dialog = new SplitFileDialogDescriptor(entry.name(), entry.size(),
            (otherPath != null ? otherPath : tab.getCurrentPath()).toString());
        showDialog(dialog, () -> split(tab, entry, dialog));
    }

    private static void split(FileManagerTab tab, FileEntry entry, SplitFileDialogDescriptor dialog) {

        Path targetDir = Path.of(dialog.getTargetDirectory());
        long chunkSize = dialog.isSplitBySize()
            ? dialog.getChunkSize()
            : (entry.size() + dialog.getNumberOfParts() - 1) / dialog.getNumberOfParts();

        BackgroundProgress.run(tab.getProject(),
            LocalizeValue.localizeTODO("Splitting " + entry.name()), indicator -> {
                try {
                    Files.createDirectories(targetDir);
                }
                catch (Exception e) {
                    notifyError(tab, "Failed to create target directory: " + FileUtils.fileErrorMessage(e));
                    return;
                }

                indicator.setIndeterminate(false);
                try {
                    SplitFileOperation.split(entry.path(), targetDir, chunkSize,
                        byteProgress(indicator, "Writing chunk"), indicator::isCanceled);
                    if (!indicator.isCanceled()) {
                        int totalChunks = entry.size() == 0L
                            ? 1
                            : (int) ((entry.size() + chunkSize - 1) / chunkSize);
                        notifyInfo(tab, entry.name() + " split into " + totalChunks + " parts");
                    }
                }
                catch (Exception e) {
                    if (!indicator.isCanceled()) {
                        notifyError(tab, "Failed to split " + entry.name() + ": "
                            + FileUtils.fileErrorMessage(e));
                    }
                }

                // Chunks and the .crc file land in targetDir, which neither panel is necessarily
                // showing — invalidate so whichever one navigates there doesn't serve a stale list.
                tab.getFileOps().invalidateListingCache(targetDir);
                finishAndRefresh(tab);
            });
    }

    public static void performCombineFiles(FileManagerTab tab) {
        if (tab.isInsideArchive()) {
            return;
        }
        FileEntry entry = tab.getSelectedEntry();
        if (entry == null || entry.directory() || entry.parentLink()) {
            return;
        }
        Path directory = entry.path().getParent();
        String baseFileName = CombineFilesOperation.resolveBaseFileName(entry.path());
        if (directory == null || baseFileName == null) {
            return;
        }
        Path otherPath = tab.getOtherPanelPath();
        String defaultTarget = (otherPath != null ? otherPath : tab.getCurrentPath()).toString();

        // The chunk scan probes for each index and stats every chunk — thousands of calls for a
        // finely-split file — so discovery runs off the UI thread, hopping back only for dialogs.
        UiTask.background(() -> combineFiles(tab, directory, baseFileName, defaultTarget));
    }

    private static void combineFiles(FileManagerTab tab, Path directory, String baseFileName, String defaultTarget) {
        Path crcFile = directory.resolve(baseFileName + ".crc");
        boolean hasCrc = Files.exists(crcFile);

        CombineFilesOperation.CrcInfo crcInfo = null;
        if (hasCrc) {
            try {
                crcInfo = CombineFilesOperation.parseCrcFile(crcFile);
            }
            catch (Exception e) {
                notifyError(tab, "Failed to parse CRC file: " + FileUtils.fileErrorMessage(e));
                return;
            }
        }

        String targetFileName = crcInfo != null ? crcInfo.filename() : baseFileName;
        List<Path> chunkFiles = CombineFilesOperation.findChunkFiles(directory, targetFileName);
        if (chunkFiles.isEmpty()) {
            notifyError(tab, "No chunk files found for " + baseFileName);
            return;
        }

        long totalSize = 0;
        for (Path chunk : chunkFiles) {
            try {
                totalSize += Files.size(chunk);
            }
            catch (Exception e) {
                // A chunk we can't stat still counts toward the list; the combine itself reports it.
            }
        }

        CombineFilesOperation.CrcInfo info = crcInfo;
        long sizeForDialog = totalSize;
        tab.getUIAccess().give(() -> {
            CombineFilesDialogDescriptor dialog = new CombineFilesDialogDescriptor(chunkFiles.size(),
                sizeForDialog, hasCrc, defaultTarget, targetFileName);
            showDialog(dialog, () -> {
                String targetDirectory = dialog.getTargetDirectory();
                String targetFile = dialog.getTargetFile();
                UiTask.background(() -> runCombine(tab, chunkFiles, info, hasCrc, targetDirectory, targetFile));
            });
        });
    }

    private static void runCombine(FileManagerTab tab, List<Path> chunkFiles,
                                   CombineFilesOperation.@Nullable CrcInfo crcInfo, boolean hasCrc,
                                   String targetDirectory, String targetFileName) {
        Path targetDir = Path.of(targetDirectory);
        try {
            Files.createDirectories(targetDir);
        }
        catch (Exception e) {
            notifyError(tab, "Failed to create target directory: " + FileUtils.fileErrorMessage(e));
            return;
        }
        Path targetFile = targetDir.resolve(targetFileName);

        if (Files.exists(targetFile) && !confirmOverwrite(tab, targetFileName)) {
            return;
        }

        BackgroundProgress.run(tab.getProject(),
            LocalizeValue.localizeTODO("Combining " + targetFileName), indicator -> {
                indicator.setIndeterminate(false);
                try {
                    CombineFilesOperation.combine(chunkFiles, targetFile,
                        crcInfo != null ? crcInfo.size() : null,
                        crcInfo != null ? crcInfo.crc32() : null,
                        byteProgress(indicator, "Reading chunk"), indicator::isCanceled);
                    if (!indicator.isCanceled()) {
                        notifyInfo(tab, targetFileName + " assembled from " + chunkFiles.size() + " parts"
                            + (hasCrc ? " (CRC verified)" : ""));
                    }
                }
                catch (Exception e) {
                    if (!indicator.isCanceled()) {
                        notifyError(tab, "Failed to combine files: " + FileUtils.fileErrorMessage(e));
                    }
                }

                tab.getFileOps().invalidateListingCache(targetDir);
                finishAndRefresh(tab);
            });
    }

    /** Asks from the worker thread and waits; closing the prompt answers no. */
    private static boolean confirmOverwrite(FileManagerTab tab, String fileName) {
        return askAndWait(tab, Alert.<Boolean>create()
            .asQuestion()
            .title(LocalizeValue.localizeTODO("Combine Files"))
            .text(LocalizeValue.of("File \"" + fileName + "\" already exists. Overwrite?"))
            .button(LocalizeValue.localizeTODO("Overwrite"), () -> Boolean.TRUE).asDefaultButton()
            .button(LocalizeValue.localizeTODO("Cancel"), () -> Boolean.FALSE).asExitButton(), Boolean.FALSE);
    }

    /**
     * Shows {@code alert} on the tab's UI and blocks the calling worker thread for the answer - {@code closed} when the
     * alert is dismissed without one. Never call on the UI thread.
     */
    private static <V> V askAndWait(FileManagerTab tab, Alert<V> alert, V closed) {
        CompletableFuture<V> answer = new CompletableFuture<>();
        tab.getUIAccess().give(() -> alert.showAsync()
            .whenComplete((value, error) -> answer.complete(value != null ? value : closed)));
        return answer.join();
    }

    // --- Pack and extract ----------------------------------------------------------------------------------

    public static void performPack(FileManagerTab tab) {
        List<FileEntry> selected = tab.getSelectedEntries();
        if (selected.isEmpty()) {
            return;
        }
        Path otherPath = tab.getOtherPanelPath();
        Path destination = otherPath != null ? otherPath : tab.getCurrentPath();
        String archiveName = selected.size() == 1 ? selected.get(0).name() + ".zip" : "archive.zip";

        PackDialogDescriptor dialog = new PackDialogDescriptor(selected, destination.resolve(archiveName).toString());
        showDialog(dialog, () -> pack(tab, selected, dialog));
    }

    private static void pack(FileManagerTab tab, List<FileEntry> selected, PackDialogDescriptor dialog) {

        Path archivePath = Path.of(dialog.getArchivePath());
        if (archivePath.getParent() == null) {
            archivePath = tab.getCurrentPath().resolve(archivePath);
        }
        Path finalArchivePath = archivePath;
        ArchiveFormat format = dialog.getArchiveFormat();
        boolean deleteAfterPacking = dialog.isDeleteAfterPacking();
        List<Path> sourcePaths = sourcePaths(selected);

        // The exists probe can block on a network destination — stat off the UI thread and hop
        // back only for the overwrite/append prompt.
        UiTask.background(() -> {
            boolean archiveExists = Files.exists(finalArchivePath);
            ArchiveExistsAction action = archiveExists
                ? askArchiveExistsAction(tab, finalArchivePath, format)
                : ArchiveExistsAction.OVERWRITE;
            if (action == ArchiveExistsAction.CANCEL) {
                return;
            }
            runPack(tab, finalArchivePath, sourcePaths, format, archiveExists,
                action == ArchiveExistsAction.APPEND, deleteAfterPacking);
        });
    }

    private static void runPack(FileManagerTab tab, Path archivePath, List<Path> sourcePaths, ArchiveFormat format,
                                boolean archiveExists, boolean appendToExisting, boolean deleteAfterPacking) {
        ArchiveService archiveService = tab.getProject().getInstance(ArchiveService.class);

        BackgroundProgress.run(tab.getProject(), LocalizeValue.localizeTODO("Packing files"), indicator -> {
            indicator.setText(LocalizeValue.localizeTODO("Counting files..."));
            int totalFiles = FileUtils.countFiles(sourcePaths);
            indicator.setIndeterminate(false);

            try {
                ProgressListener onProgress = countingProgress(indicator, "Packing", totalFiles);
                ErrorListener onError = errorNotifier(tab, "Failed to pack");
                int packedCount = switch (format) {
                    case ZIP -> archiveService.packZip(archivePath, sourcePaths, appendToExisting,
                        archiveExists, onProgress, onError, indicator::isCanceled);
                    case TAR_GZ -> archiveService.packTarGz(archivePath, sourcePaths, onProgress,
                        onError, indicator::isCanceled);
                };

                // An archive we created but wrote nothing into is worse than none at all.
                if (packedCount == 0 && !appendToExisting) {
                    Files.deleteIfExists(archivePath);
                }

                if (!indicator.isCanceled() && packedCount > 0 && deleteAfterPacking) {
                    indicator.setFraction(0.0);
                    indicator.setText(LocalizeValue.localizeTODO("Deleting source files..."));
                    tab.getFileOps().deleteFilesWithProgress(sourcePaths,
                        countingProgress(indicator, "Deleting", totalFiles),
                        errorNotifier(tab, "Failed to delete"),
                        indicator::isCanceled, false);
                }
            }
            catch (Exception e) {
                notifyError(tab, "Packing failed: " + FileUtils.fileErrorMessage(e));
            }

            refreshAfterPack(tab, archivePath);
        });
    }

    /** The archive may have landed in either panel's directory, or in neither. */
    private static void refreshAfterPack(FileManagerTab tab, Path archivePath) {
        Path archiveParent = archivePath.getParent();
        Path archiveFileName = archivePath.getFileName();
        String selectName = archiveFileName != null ? archiveFileName.toString() : null;

        // Neither the refresh nor the other-panel refresh invalidates the cache for the directory
        // the archive was written into, so do it here.
        if (archiveParent != null) {
            tab.getFileOps().invalidateListingCache(archiveParent);
        }

        if (archiveParent != null && archiveParent.equals(tab.getCurrentPath())) {
            refreshAfterVfsChange(tab, selectName);
            tab.getUIAccess().give(tab::refreshOtherPanel);
            return;
        }

        refreshAfterVfsChange(tab);
        Path otherPath = tab.getOtherPanelPath();
        if (archiveParent != null && otherPath != null && archiveParent.equals(otherPath)) {
            tab.getUIAccess().give(() -> {
                FileManagerTab otherTab = tab.getOtherPanelTab();
                if (otherTab != null) {
                    otherTab.navigateTo(otherTab.getCurrentPath(), selectName);
                }
            });
        }
        else {
            tab.getUIAccess().give(tab::refreshOtherPanel);
        }
    }

    public static void performExtract(FileManagerTab tab) {
        List<FileEntry> selected = selectedArchives(tab);
        if (selected.isEmpty()) {
            return;
        }
        Path otherPath = tab.getOtherPanelPath();
        Path destination = otherPath != null ? otherPath : tab.getCurrentPath();
        extractWithDialog(tab, selected, destination);
    }

    public static void performExtractHere(FileManagerTab tab) {
        List<FileEntry> selected = selectedArchives(tab);
        if (!selected.isEmpty()) {
            extractArchives(tab, sourcePaths(selected), tab.getCurrentPath(), OverwritePolicy.ASK);
        }
    }

    public static void performExtractToSubdir(FileManagerTab tab) {
        List<FileEntry> selected = selectedArchives(tab);
        if (selected.isEmpty()) {
            return;
        }
        Path destination = tab.getCurrentPath().resolve(archiveBaseName(selected.get(0).name()));
        extractWithDialog(tab, selected, destination);
    }

    private static void extractWithDialog(FileManagerTab tab, List<FileEntry> selected, Path destination) {
        ExtractDialogDescriptor dialog = new ExtractDialogDescriptor(selected, destination.toString());
        showDialog(dialog, () -> {
            Path destPath = Path.of(dialog.getDestinationPath());
            if (!destPath.isAbsolute()) {
                destPath = tab.getCurrentPath().resolve(destPath);
            }
            extractArchives(tab, sourcePaths(selected), destPath, dialog.getPolicy());
        });
    }

    private static List<FileEntry> selectedArchives(FileManagerTab tab) {
        List<FileEntry> archives = new ArrayList<>();
        for (FileEntry entry : tab.getSelectedEntries()) {
            if (!entry.directory() && VirtualFileSystemRegistry.supportsByExtension(entry.name())) {
                archives.add(entry);
            }
        }
        return archives;
    }

    /**
     * The archive name with its extension stripped, handling the compound forms ({@code foo.tar.gz}
     * → {@code foo}) as well as single-extension archives. Used as the default subdirectory name
     * for "Extract to Subdir".
     */
    public static String archiveBaseName(String archiveName) {
        String withoutLast = StringUtil.substringBeforeLast(archiveName, ".");
        if (withoutLast.isEmpty() || withoutLast.equals(archiveName)) {
            return archiveName;
        }
        return StringUtil.endsWithIgnoreCase(withoutLast, ".tar")
            ? StringUtil.substringBeforeLast(withoutLast, ".")
            : withoutLast;
    }

    public static void extractArchives(FileManagerTab tab, List<Path> archivePaths, Path destination,
                                       OverwritePolicy initialPolicy) {
        ArchiveService archiveService = tab.getProject().getInstance(ArchiveService.class);

        BackgroundProgress.run(tab.getProject(), LocalizeValue.localizeTODO("Extracting files"), indicator -> {
            // Also reports — and allows cancelling — the temp-dir extraction that formats without a
            // header-only entry count (.rar, .iso, a corrupt .zip) perform when their filesystem is
            // opened, which would otherwise be invisible.
            VfsOpenProgress openProgress = VfsOpenProgress.fromIndicator(indicator);

            for (Path archivePath : archivePaths) {
                if (indicator.isCanceled()) {
                    break;
                }
                indicator.setIndeterminate(true);
                indicator.setText(LocalizeValue.localizeTODO(
                    "Counting entries in " + archivePath.getFileName() + "..."));
                int totalEntries = archiveService.countArchiveEntries(archivePath, openProgress);
                indicator.setIndeterminate(false);

                archiveService.extractArchiveWithProgress(archivePath, destination, initialPolicy,
                    countingProgress(indicator, "Extracting", totalEntries),
                    path -> askOverwriteConfirm(tab, path),
                    errorNotifier(tab, "Failed to extract"),
                    indicator::isCanceled,
                    openProgress);
            }

            tab.getFileOps().invalidateListingCache(destination);
            finishAndRefresh(tab);
        });
    }

    // --- Opening -----------------------------------------------------------------------------------------

    public static void openFile(FileManagerTab tab, FileEntry entry) {
        if (entry.namedPipe()) {
            // Opening a FIFO blocks until a writer connects, which would hang the editor.
            TurtleCommanderNotifications.warn(tab.getProject(),
                entry.name() + " is a named pipe (FIFO) and cannot be opened");
            return;
        }
        VirtualFileSystem vfs = tab.getCurrentVfs();
        if (vfs != null) {
            if (vfs.isReadOnly()) {
                openVfsFileReadOnly(tab, entry, vfs);
            }
            else {
                openVfsFileEditable(tab, entry);
            }
            return;
        }
        UiTask.background(() -> openInEditor(tab, entry.path()));
    }

    /** Resolves and pre-caches the file type off the UI thread, then opens the editor on it. */
    private static void openInEditor(FileManagerTab tab, Path path) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        if (virtualFile == null) {
            return;
        }
        virtualFile.getFileType();
        tab.getUIAccess().give(() -> FileEditorManager.getInstance(tab.getProject()).openFile(virtualFile, true));
    }

    private static void openVfsFileReadOnly(FileManagerTab tab, FileEntry entry, VirtualFileSystem vfs) {
        boolean isTempDirVfs = !(vfs instanceof ZipVirtualFileSystem);
        UiTask.background(() -> {
            try {
                Path filePath;
                if (isTempDirVfs) {
                    // Force a lazy filesystem to stream this file's bytes onto its stub before the
                    // editor opens it. A no-op for the fully-materialised ones.
                    vfs.materialize(entry.path());
                    filePath = entry.path();
                }
                else {
                    filePath = materializeVfsEntryToTemp(entry, "turtle-vfs-view-");
                }
                openInEditor(tab, filePath);
            }
            catch (Exception e) {
                notifyError(tab, "Failed to open file: " + FileUtils.fileErrorMessage(e));
            }
        });
    }

    private static void openVfsFileEditable(FileManagerTab tab, FileEntry entry) {
        Path vfsFilePath = entry.path();
        List<VfsStackEntry> stackRef = new ArrayList<>(tab.getVfsStack());

        UiTask.background(() -> {
            try {
                Path tempPath = materializeVfsEntryToTemp(entry, "turtle-vfs-edit-");
                VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempPath);
                if (virtualFile == null) {
                    return;
                }
                virtualFile.getFileType();

                // Capture the archive-relative path now, while vfsFilePath is still valid: the
                // write-back re-resolves the destination from it against the current root, so a
                // later flush — this file's own re-save, or another file from the same archive —
                // can't leave it pointing at a deleted temp dir.
                VirtualFileSystem innerVfs = stackRef.isEmpty()
                    ? null
                    : stackRef.get(stackRef.size() - 1).getVfs();
                String vfsFileRel = innerVfs != null
                    ? VfsPaths.vfsRelativePath(innerVfs.getRoot(), vfsFilePath)
                    : "";

                tab.getProject().getInstance(VfsEditService.class).trackEdit(new VfsEditEntry(
                    vfsFilePath, tempPath, stackRef, vfsFileRel, tab.getVfsWriteLock(),
                    () -> {
                        VirtualFileSystem current = tab.getCurrentVfs();
                        return current != null
                            ? VfsPaths.vfsRelativePath(current.getRoot(), tab.getCurrentPath())
                            : "";
                    },
                    relPath -> {
                        VirtualFileSystem current = tab.getCurrentVfs();
                        if (current == null) {
                            return;
                        }
                        Path newPath = relPath.isEmpty()
                            ? current.getRoot()
                            : current.getRoot().resolve(relPath);
                        UiTask.background(() -> tab.navigateTo(newPath));
                    }));

                tab.getUIAccess().give(() -> FileEditorManager.getInstance(tab.getProject()).openFile(virtualFile, true));
            }
            catch (Exception e) {
                notifyError(tab, "Failed to open file: " + FileUtils.fileErrorMessage(e));
            }
        });
    }

    /**
     * Copies an archive-internal file out to a fresh local temp directory so code that only
     * understands real paths — the editor, the OS file-association handler — can see it. Both the
     * file and its parent are marked delete-on-exit, and the stale-temp sweep runs so abandoned
     * earlier extractions don't accumulate. The prefix exists purely so a leak in {@code /tmp} can
     * be traced back to its source.
     */
    private static Path materializeVfsEntryToTemp(FileEntry entry, String prefix) throws Exception {
        VfsTempCleanup.cleanupOnce();
        // Pull the bytes into the stub first if this filesystem materialises lazily — otherwise the
        // copy below sees an empty sparse file and the user opens a zero-byte view of their file.
        OpenVfsRegistry.materializeIfNeeded(entry.path());

        Path tempDir = Files.createTempDirectory(prefix);
        Path tempPath = tempDir.resolve(entry.path().getFileName().toString());
        Files.copy(entry.path(), tempPath);
        tempPath.toFile().deleteOnExit();
        tempDir.toFile().deleteOnExit();
        return tempPath;
    }

    public static void openSelectedInAssociatedApp(FileManagerTab tab) {
        FileEntry entry = tab.getSelectedEntry();
        if (entry == null || entry.parentLink() || entry.directory()) {
            return;
        }
        VirtualFileSystem vfs = tab.getCurrentVfs();
        boolean needsTempCopy = vfs instanceof ZipVirtualFileSystem;

        UiTask.background(() -> {
            try {
                Path filePath = needsTempCopy
                    ? materializeVfsEntryToTemp(entry, "turtle-vfs-app-")
                    : entry.path();
                // Desktop.open resolves the association and launches the handler synchronously;
                // the JDK explicitly warns against calling it on the UI thread.
                Desktop.getDesktop().open(filePath.toFile());
            }
            catch (Exception e) {
                notifyError(tab, "Failed to open file: " + FileUtils.fileErrorMessage(e));
            }
        });
    }

    public static void viewSelectedFile(FileManagerTab tab) {
        FileEntry entry = tab.getSelectedEntry();
        if (entry != null && !entry.parentLink() && !entry.directory()) {
            openFile(tab, entry);
        }
    }

    // --- Rename -------------------------------------------------------------------------------------------

    public static void startRename(FileManagerTab tab) {
        FileEntry entry = tab.getSelectedEntry();
        if (entry == null || entry.parentLink()) {
            return;
        }
        // Only the Swing table has an inline editor; everything else asks in a prompt.
        if (tab instanceof FileTab swingTab && swingTab.getViewMode() == ViewMode.TABLE) {
            int row = swingTab.getTable().getSelectedRow();
            if (row >= 0) {
                swingTab.getTable().editCellAt(row, nameViewColumn(swingTab));
            }
            return;
        }
        InputBoxBuilder.text()
            .title(LocalizeValue.localizeTODO("Rename"))
            .text(LocalizeValue.localizeTODO("Enter new name:"))
            .value(entry.name())
            .showAsync()
            .whenComplete((newName, error) -> {
                if (error == null && newName != null && !newName.isBlank() && !newName.equals(entry.name())) {
                    performRename(tab, entry, newName);
                }
            });
    }

    private static int nameViewColumn(FileTab tab) {
        for (int i = 0; i < tab.getTable().getColumnCount(); i++) {
            if (tab.getTable().convertColumnIndexToModel(i) == FileTableModel.COL_NAME) {
                return i;
            }
        }
        return 0;
    }

    public static void performRename(FileManagerTab tab, FileEntry entry, String newName) {
        UiTask.background(() -> {
            try {
                VirtualFileSystem vfs = tab.getCurrentVfs();
                if (vfs != null) {
                    renameInArchive(tab, vfs, entry, newName);
                }
                else {
                    renameOnDisk(tab, entry, newName);
                }
            }
            catch (Exception e) {
                notifyError(tab, "Rename failed: " + FileUtils.fileErrorMessage(e));
            }
        });
    }

    private static void renameInArchive(FileManagerTab tab, VirtualFileSystem vfs, FileEntry entry, String newName)
        throws Exception {
        if (vfs.isReadOnly()) {
            notifyError(tab, "Cannot rename in a read-only archive");
            return;
        }
        vfs.renameFile(entry.path(), newName);
        String relative = VfsPaths.vfsRelativePath(vfs.getRoot(), tab.getCurrentPath());
        Path newPath = relative.isEmpty() ? vfs.getRoot() : vfs.getRoot().resolve(relative);
        tab.navigateTo(newPath, newName);
    }

    private static void renameOnDisk(FileManagerTab tab, FileEntry entry, String newName) throws Exception {
        // Prefer the platform's own virtual file system so open editors follow the rename.
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByNioFile(entry.path());
        if (virtualFile != null) {
            UiTask.uiAndWait(() -> {
                try {
                    virtualFile.rename(FileTabOperationUtil.class, newName);
                }
                catch (Exception e) {
                    notifyError(tab, "Rename failed: " + FileUtils.fileErrorMessage(e));
                }
            });
            // FileOperationService.renameFile invalidates the cache itself; this path bypasses it.
            Path parent = entry.path().getParent();
            if (parent != null) {
                tab.getFileOps().invalidateListingCache(parent);
            }
        }
        else {
            tab.getFileOps().renameFile(entry.path(), newName);
        }
        tab.navigateTo(tab.getCurrentPath(), newName);
    }

    // --- Multi-rename ---------------------------------------------------------------------------------------

    /**
     * The last multi-rename, as original-to-final path pairs, so Undo can reverse it. A single slot
     * per plugin process — rerunning multi-rename overwrites it. Held statically rather than per
     * tab so the undo works whichever panel is focused, since the user may switch tabs in between.
     */
    private static volatile @Nullable List<Path[]> ourLastMultiRenameBatch;

    public static void performMultiRename(FileManagerTab tab) {
        VirtualFileSystem vfs = tab.getCurrentVfs();
        if (vfs != null && vfs.isReadOnly()) {
            tab.fileErrorNotification("Cannot rename inside a read-only archive");
            return;
        }

        List<FileEntry> targets = withoutParentLink(tab.getSelectedEntries());
        if (targets.isEmpty()) {
            // Nothing selected means "every entry in the current view".
            targets = withoutParentLink(tab.getDisplayedEntries());
        }
        if (targets.isEmpty()) {
            tab.fileErrorNotification("Nothing to rename");
            return;
        }

        MultiRenameDialogDescriptor dialog = new MultiRenameDialogDescriptor(targets);
        showDialog(dialog, () -> {
            List<MultiRenameDialogDescriptor.Rename> renames = dialog.getResult();
            if (!renames.isEmpty()) {
                UiTask.background(() -> runMultiRename(tab, renames));
            }
        });
    }

    /**
     * Two-phase rename so cyclic swaps like a↔b survive: every source is first moved to a uniquely
     * named temp file, then each temp to its final name. A direct a→b would collide when b is
     * itself waiting to be renamed.
     */
    private static void runMultiRename(FileManagerTab tab, List<MultiRenameDialogDescriptor.Rename> renames) {
        List<Path[]> completed = new ArrayList<>();
        List<Object[]> temps = new ArrayList<>();

        try {
            for (MultiRenameDialogDescriptor.Rename rename : renames) {
                Path parent = rename.entry().path().getParent();
                if (parent == null) {
                    continue;
                }
                Path temp = uniqueTemp(parent, rename.entry().path().getFileName().toString());
                Files.move(rename.entry().path(), temp);
                temps.add(new Object[]{rename.entry().path(), temp, rename.newName()});
            }
            for (Object[] entry : temps) {
                Path original = (Path) entry[0];
                Path temp = (Path) entry[1];
                Path finalPath = temp.resolveSibling((String) entry[2]);
                Files.move(temp, finalPath);
                completed.add(new Path[]{original, finalPath});
            }
        }
        catch (Exception e) {
            rollbackMultiRename(temps, completed);
            notifyError(tab, "Multi-rename failed: " + FileUtils.fileErrorMessage(e));
            return;
        }

        invalidateParents(tab, completed, 0);
        ourLastMultiRenameBatch = completed;
        notifyInfo(tab, "Renamed " + completed.size() + " file(s).");
        tab.navigateTo(tab.getCurrentPath());
    }

    /** Best-effort restore of the sources that were moved to temps but never got their final name. */
    private static void rollbackMultiRename(List<Object[]> temps, List<Path[]> completed) {
        Set<Path> done = new LinkedHashSet<>();
        for (Path[] pair : completed) {
            done.add(pair[0]);
        }
        for (Object[] entry : temps) {
            Path original = (Path) entry[0];
            Path temp = (Path) entry[1];
            if (done.contains(original) || !Files.exists(temp)) {
                continue;
            }
            try {
                Files.move(temp, original);
            }
            catch (Exception e) {
                // Nothing more we can do; the temp file is left for the user to recover.
            }
        }
    }

    /** Whether a batch is available to undo, so the Undo action can enable itself. */
    public static boolean hasMultiRenameUndo() {
        List<Path[]> batch = ourLastMultiRenameBatch;
        return batch != null && !batch.isEmpty();
    }

    public static void performMultiRenameUndo(FileManagerTab tab) {
        List<Path[]> batch = ourLastMultiRenameBatch;
        if (batch == null || batch.isEmpty()) {
            tab.fileErrorNotification("Nothing to undo");
            return;
        }
        UiTask.background(() -> {
            try {
                // Reversed through the same two-phase trick, for the same reason.
                List<Object[]> temps = new ArrayList<>();
                for (Path[] pair : batch) {
                    Path current = pair[1];
                    Path parent = current.getParent();
                    if (parent == null || !Files.exists(current)) {
                        continue;
                    }
                    Path temp = uniqueTemp(parent, current.getFileName().toString());
                    Files.move(current, temp);
                    temps.add(new Object[]{temp, pair[0]});
                }
                for (Object[] entry : temps) {
                    Files.move((Path) entry[0], (Path) entry[1]);
                }

                invalidateParents(tab, batch, 0);
                invalidateParents(tab, batch, 1);
                ourLastMultiRenameBatch = null;
                tab.navigateTo(tab.getCurrentPath());
            }
            catch (Exception e) {
                notifyError(tab, "Undo failed: " + FileUtils.fileErrorMessage(e));
            }
        });
    }

    private static void invalidateParents(FileManagerTab tab, List<Path[]> pairs, int index) {
        Set<Path> parents = new LinkedHashSet<>();
        for (Path[] pair : pairs) {
            Path parent = pair[index].getParent();
            if (parent != null) {
                parents.add(parent);
            }
        }
        for (Path parent : parents) {
            tab.getFileOps().invalidateListingCache(parent);
        }
    }

    private static Path uniqueTemp(Path parent, String seed) {
        for (int i = 0; ; i++) {
            Path candidate = parent.resolve(".tc-multirename-" + seed + "." + i + ".tmp");
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
    }

    // --- Prompts ---------------------------------------------------------------------------------------------

    /** The outcome of the "archive already exists" prompt, independent of which buttons were shown. */
    private enum ArchiveExistsAction {
        OVERWRITE, APPEND, CANCEL
    }

    /** Appending is only offered for formats that support it - currently ZIP. Closing the prompt cancels. */
    private static ArchiveExistsAction askArchiveExistsAction(FileManagerTab tab, Path archivePath,
                                                              ArchiveFormat format) {
        Alert<ArchiveExistsAction> alert = Alert.<ArchiveExistsAction>create()
            .asQuestion()
            .title(LocalizeValue.localizeTODO("Archive Exists"))
            .text(LocalizeValue.of("Archive already exists:\n" + archivePath.getFileName() + "\n\nWhat would you like to do?"))
            .button(LocalizeValue.localizeTODO("Overwrite"), () -> ArchiveExistsAction.OVERWRITE).asDefaultButton();
        if (format == ArchiveFormat.ZIP) {
            alert.button(LocalizeValue.localizeTODO("Add to Existing"), () -> ArchiveExistsAction.APPEND);
        }
        alert.button(LocalizeValue.localizeTODO("Cancel"), () -> ArchiveExistsAction.CANCEL).asExitButton();
        return askAndWait(tab, alert, ArchiveExistsAction.CANCEL);
    }

    /**
     * Asks, from the transfer's worker thread, what to do with a file that is already there, and waits for the answer.
     * Closing the prompt answers cancel, so the user can't fall into a stale default by accident.
     */
    private static OverwriteResponse askOverwriteConfirm(FileManagerTab tab, Path path) {
        CompletableFuture<OverwriteResponse> answer = new CompletableFuture<>();
        tab.getUIAccess().give(() -> Alert.<OverwriteResponse>create()
            .asQuestion()
            .title(LocalizeValue.localizeTODO("File Exists"))
            .text(LocalizeValue.of("File already exists:\n" + path.getFileName() + "\n\nWhat would you like to do?"))
            .button(LocalizeValue.localizeTODO("Overwrite"), () -> OverwriteResponse.OVERWRITE).asDefaultButton()
            .button(LocalizeValue.localizeTODO("Skip"), () -> OverwriteResponse.SKIP)
            .button(LocalizeValue.localizeTODO("Overwrite All"), () -> OverwriteResponse.OVERWRITE_ALL)
            .button(LocalizeValue.localizeTODO("Skip All"), () -> OverwriteResponse.SKIP_ALL)
            .button(LocalizeValue.localizeTODO("Overwrite if Older"), () -> OverwriteResponse.OVERWRITE_IF_OLDER)
            .button(LocalizeValue.localizeTODO("Overwrite All Older"), () -> OverwriteResponse.OVERWRITE_ALL_IF_OLDER)
            .button(LocalizeValue.localizeTODO("Cancel"), () -> OverwriteResponse.CANCEL).asExitButton()
            .showAsync()
            .whenComplete((response, error) -> answer.complete(response != null ? response : OverwriteResponse.CANCEL)));
        return answer.join();
    }

    // --- Shared plumbing ---------------------------------------------------------------------------------------

    private static ProgressListener countingProgress(ProgressIndicator indicator, String verb, int total) {
        return (count, name) -> {
            indicator.setFraction(total > 0 ? (double) count / total : 1.0);
            indicator.setText(LocalizeValue.localizeTODO(verb + " " + count + " / " + total));
            indicator.setText2(LocalizeValue.of(name));
        };
    }

    private static SplitFileOperation.ProgressCallback byteProgress(ProgressIndicator indicator, String verb) {
        return (chunkIndex, totalChunks, bytesWritten, totalBytes) -> {
            indicator.setFraction(totalBytes > 0 ? (double) bytesWritten / totalBytes : 1.0);
            indicator.setText(LocalizeValue.localizeTODO(
                verb + " " + chunkIndex + " of " + totalChunks));
            indicator.setText2(LocalizeValue.localizeTODO(
                FileUtils.formatSize(bytesWritten) + " / " + FileUtils.formatSize(totalBytes)));
        };
    }

    private static ErrorListener errorNotifier(FileManagerTab tab, String prefix) {
        return (path, error) -> notifyError(tab,
            prefix + " " + path.getFileName() + ": " + FileUtils.fileErrorMessage(error));
    }

    private static void notifyError(FileManagerTab tab, String message) {
        tab.getUIAccess().give(() -> tab.fileErrorNotification(message));
    }

    private static void notifyInfo(FileManagerTab tab, String message) {
        tab.getUIAccess().give(() -> TurtleCommanderNotifications.info(tab.getProject(), message));
    }

    private static void finishAndRefresh(FileManagerTab tab) {
        refreshAfterVfsChange(tab);
        tab.getUIAccess().give(tab::refreshOtherPanel);
    }

    private static List<Path> sourcePaths(List<FileEntry> entries) {
        List<Path> paths = new ArrayList<>(entries.size());
        for (FileEntry entry : entries) {
            paths.add(entry.path());
        }
        return paths;
    }

    private static List<FileEntry> withoutParentLink(List<FileEntry> entries) {
        List<FileEntry> result = new ArrayList<>(entries.size());
        for (FileEntry entry : entries) {
            if (!entry.parentLink()) {
                result.add(entry);
            }
        }
        return result;
    }

    /** An archive is only ever open in the Swing tab; the unified one lists the plain file system again. */
    private static void refreshAfterVfsChange(FileManagerTab tab) {
        if (tab instanceof FileTab swingTab) {
            FileTabVfsUtil.refreshAfterVfsChange(swingTab);
        }
        else {
            tab.refresh();
        }
    }

    private static void refreshAfterVfsChange(FileManagerTab tab, @Nullable String selectName) {
        if (tab instanceof FileTab swingTab) {
            FileTabVfsUtil.refreshAfterVfsChange(swingTab, selectName);
        }
        else {
            tab.getFileOps().invalidateListingCache(tab.getCurrentPath());
            tab.navigateTo(tab.getCurrentPath(), selectName);
        }
    }
}
