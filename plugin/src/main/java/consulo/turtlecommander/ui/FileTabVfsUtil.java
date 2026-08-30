package consulo.turtlecommander.ui;

import consulo.application.progress.ProgressIndicator;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.service.VfsTempCleanup;
import consulo.turtlecommander.util.BackgroundProgress;
import consulo.turtlecommander.util.FileUtils;
import consulo.turtlecommander.util.UiTask;
import consulo.turtlecommander.vfs.OpenVfsRegistry;
import consulo.turtlecommander.vfs.SilentVfsOpenException;
import consulo.turtlecommander.vfs.VfsOpenProgress;
import consulo.turtlecommander.vfs.VfsPaths;
import consulo.turtlecommander.vfs.VfsStackEntry;
import consulo.turtlecommander.vfs.VirtualFileSystem;
import consulo.turtlecommander.vfs.VirtualFileSystemRegistry;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

/** Entering, leaving and tearing down the nested-archive stack. */
public final class FileTabVfsUtil {
    private static final Logger LOG = Logger.getInstance(FileTabVfsUtil.class);

    private FileTabVfsUtil() {
    }

    /**
     * Closes every open filesystem in the stack and deletes their temp dirs <em>synchronously</em>.
     * Used from dispose: the {@code Disposable} contract requires cleanup to have finished when
     * dispose returns, so it can't be deferred. For the mid-life "user left the archive" paths use
     * {@link #closeVfsStackAsync} instead.
     */
    public static void closeVfsStack(FileTab tab) {
        List<VfsStackEntry> stack = tab.getVfsStack();
        for (int i = stack.size() - 1; i >= 0; i--) {
            closeQuietly(stack.get(i));
        }
        stack.clear();
    }

    /**
     * Leaves the archive mid-life (drive selection, breadcrumb click): empties the stack
     * synchronously so the tab immediately reflects "left the archive", then closes the handles off
     * the UI thread. Unlike dispose, the tab keeps living.
     */
    public static void closeVfsStackAsync(FileTab tab) {
        if (tab.getVfsStack().isEmpty()) {
            return;
        }
        List<VfsStackEntry> entries = new ArrayList<>(tab.getVfsStack());
        tab.getVfsStack().clear();
        scheduleVfsClose(tab, entries);
    }

    /**
     * Closes the given filesystems and deletes their extracted temp dirs off the UI thread, holding
     * the tab's write lock.
     *
     * <p>{@code close()} recursively deletes a potentially large temp tree — which would freeze the
     * UI thread if done inline — and must not run while an in-flight write-back is copying through
     * the same stack, which would delete a temp dir mid-copy. Anything still missed is swept by
     * {@code VfsTempCleanup} on the next start.
     */
    public static void scheduleVfsClose(FileTab tab, List<VfsStackEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        Lock lock = tab.getVfsWriteLock();
        UiTask.background(() -> {
            lock.lock();
            try {
                for (VfsStackEntry entry : entries) {
                    closeQuietly(entry);
                }
            }
            finally {
                lock.unlock();
            }
        });
    }

    /**
     * The path prefix shown in the breadcrumb bar for each level of the stack, plus the separator
     * that joins archive-internal segments.
     */
    public static VfsStackPrefixes buildVfsStackPrefixes(FileTab tab) {
        List<VfsStackEntry> stack = tab.getVfsStack();
        List<String> prefixes = new ArrayList<>(stack.size());

        // The separator follows the outermost archive's own path, not the host OS: inside an
        // archive the nested segments are appended to that string and must read consistently.
        String separator = stack.get(0).getParentPath().toString().contains("\\") ? "\\" : "/";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stack.size(); i++) {
            VfsStackEntry entry = stack.get(i);
            if (i == 0) {
                sb.append(entry.getParentPath());
            }
            else {
                // Nested archives read as "outer.zip/sub/inner.zip", so the display path keeps
                // growing rather than resetting to the temp-dir location the entry lives at.
                VirtualFileSystem parentVfs = stack.get(i - 1).getVfs();
                String nested = VfsPaths.vfsRelativePath(parentVfs.getRoot(), entry.getParentPath());
                sb.append(separator).append(StringUtil.trimLeading(nested, '/').replace("/", separator));
            }
            prefixes.add(sb.toString());
        }
        return new VfsStackPrefixes(separator, prefixes);
    }

    /** Opens {@code entry} as a nested filesystem and navigates into its root. */
    public static void enterVfs(FileTab tab, FileEntry entry) {
        Path archivePath = entry.path();
        Path fileNamePath = archivePath.getFileName();
        String fileName = fileNamePath != null ? fileNamePath.toString() : "archive";

        BackgroundProgress.run(tab.getProject(),
            LocalizeValue.localizeTODO("Opening " + fileName + "\u2026"),
            indicator -> {
                VfsOpenProgress progress = VfsOpenProgress.fromIndicator(indicator);
                if (tab.getVfsStack().isEmpty()) {
                    openOutermost(tab, entry, archivePath, progress, indicator);
                }
                else {
                    openNested(tab, entry, archivePath, fileName, progress, indicator);
                }
            });
    }

    private static void openOutermost(FileTab tab, FileEntry entry, Path archivePath,
                                      VfsOpenProgress progress, ProgressIndicator indicator) {
        VirtualFileSystem vfs;
        try {
            vfs = VirtualFileSystemRegistry.create(archivePath, progress);
        }
        catch (SilentVfsOpenException e) {
            // Recognised extension but unrecognised contents — open it as a normal file instead.
            UiTask.ui(() -> FileTabOperationUtil.openFile(tab, entry));
            return;
        }
        catch (Exception e) {
            tab.fileErrorNotification("Cannot open archive: " + FileUtils.fileErrorMessage(e));
            return;
        }

        if (indicator.isCanceled()) {
            closeQuietly(new VfsStackEntry(vfs, archivePath, null));
            return;
        }
        tab.getVfsStack().add(new VfsStackEntry(vfs, archivePath, null));
        FileTabNavigationUtil.navigateTo(tab, vfs.getRoot());
    }

    private static void openNested(FileTab tab, FileEntry entry, Path archivePath, String fileName,
                                   VfsOpenProgress progress, ProgressIndicator indicator) {
        Path tempPath = null;
        try {
            VfsTempCleanup.cleanupOnce();
            // If the parent VFS is lazy (an .iso), the entry we are about to nest into is still a
            // sparse stub on disk — stream the real bytes before copying. A no-op for any VFS that
            // already extracted everything up front.
            OpenVfsRegistry.materializeIfNeeded(archivePath);
            Path tempDir = Files.createTempDirectory("turtle-vfs-");
            tempPath = tempDir.resolve(fileName);
            Files.copy(archivePath, tempPath);

            VirtualFileSystem vfs = VirtualFileSystemRegistry.create(tempPath, progress);
            if (indicator.isCanceled()) {
                closeQuietly(new VfsStackEntry(vfs, archivePath, tempPath.toFile()));
                return;
            }
            tab.getVfsStack().add(new VfsStackEntry(vfs, archivePath, tempPath.toFile()));
            FileTabNavigationUtil.navigateTo(tab, vfs.getRoot());
        }
        catch (SilentVfsOpenException e) {
            deleteTempQuietly(tempPath);
            UiTask.ui(() -> FileTabOperationUtil.openFile(tab, entry));
        }
        catch (Exception e) {
            deleteTempQuietly(tempPath);
            tab.fileErrorNotification("Cannot open nested archive: " + FileUtils.fileErrorMessage(e));
        }
    }

    /** Leaves the innermost archive, landing on its container with the archive itself selected. */
    public static void exitVfs(FileTab tab) {
        List<VfsStackEntry> stack = tab.getVfsStack();
        if (stack.isEmpty()) {
            return;
        }
        VfsStackEntry entry = stack.remove(stack.size() - 1);
        // Close off the UI thread under the write lock: closing here (Backspace at an archive root)
        // would otherwise run the recursive temp-dir delete inline and could race an in-flight
        // write-back through the same stack.
        scheduleVfsClose(tab, List.of(entry));

        Path parentPath = entry.getParentPath();
        Path nameOfArchive = parentPath.getFileName();
        String selectName = nameOfArchive != null ? nameOfArchive.toString() : "";

        Path target;
        if (!stack.isEmpty()) {
            Path parent = parentPath.getParent();
            target = parent != null ? parent : stack.get(stack.size() - 1).getVfs().getRoot();
        }
        else {
            Path parent = parentPath.getParent();
            target = parent != null ? parent : parentPath;
        }
        Path navigationTarget = target;
        UiTask.background(() -> FileTabNavigationUtil.navigateTo(tab, navigationTarget, selectName, true));
    }

    /**
     * Handles a click on a breadcrumb segment while inside an archive: either pops back out onto
     * the real filesystem, or unwinds the stack to the clicked archive level.
     */
    public static void handleVfsBreadcrumbClick(FileTab tab, String segmentPath) {
        List<VfsStackEntry> stack = tab.getVfsStack();
        if (stack.isEmpty()) {
            return;
        }
        String outerArchive = stack.get(0).getParentPath().toString();

        if (segmentPath.length() < outerArchive.length()) {
            closeVfsStackAsync(tab);
            Path path;
            try {
                path = Path.of(segmentPath);
            }
            catch (Exception e) {
                return;
            }
            // Stat off the UI thread: a dead network path can block for many seconds.
            UiTask.background(() -> {
                if (Files.isDirectory(path)) {
                    FileTabNavigationUtil.navigateTo(tab, path);
                }
            });
            return;
        }

        VfsStackPrefixes prefixes = buildVfsStackPrefixes(tab);
        int targetLevel = -1;
        for (int i = prefixes.prefixes().size() - 1; i >= 0; i--) {
            if (segmentPath.length() >= prefixes.prefixes().get(i).length()) {
                targetLevel = i;
                break;
            }
        }
        if (targetLevel < 0) {
            return;
        }

        List<VfsStackEntry> removed = new ArrayList<>();
        while (stack.size() > targetLevel + 1) {
            removed.add(stack.remove(stack.size() - 1));
        }
        scheduleVfsClose(tab, removed);

        VirtualFileSystem vfs = stack.get(stack.size() - 1).getVfs();
        String relative = StringUtil.trimStart(segmentPath, prefixes.prefixes().get(targetLevel));
        relative = StringUtil.trimLeading(StringUtil.trimLeading(relative, '\\'), '/').replace("\\", "/");
        Path vfsPath = relative.isEmpty() ? vfs.getRoot() : vfs.getPath("/" + relative);
        UiTask.background(() -> FileTabNavigationUtil.navigateTo(tab, vfsPath));
    }

    /**
     * Copies each nested archive's edited temp file back into its container, innermost first, and
     * re-resolves the stack entries' paths against the repacked parents.
     *
     * <p>The caller must hold the tab's write lock and must not be on the UI thread. Split out so
     * callers that also need to flush the innermost filesystem atomically can do both under one
     * lock acquisition.
     */
    public static void writeBackNestedArchivesLocked(FileTab tab) throws IOException {
        List<VfsStackEntry> stack = tab.getVfsStack();
        for (int i = stack.size() - 1; i > 0; i--) {
            VfsStackEntry entry = stack.get(i);
            if (entry.getTempFile() == null) {
                continue;
            }
            VfsStackEntry parentEntry = stack.get(i - 1);
            Files.copy(entry.getTempFile().toPath(), entry.getParentPath(),
                StandardCopyOption.REPLACE_EXISTING);
            String relative = VfsPaths.vfsRelativePath(parentEntry.getVfs().getRoot(), entry.getParentPath());
            parentEntry.getVfs().flush();
            entry.setParentPath(relative.isEmpty()
                ? parentEntry.getVfs().getRoot()
                : parentEntry.getVfs().getRoot().resolve(relative));
        }
    }

    /** Flushes the innermost filesystem and writes every nested archive back, then re-lists. */
    public static void refreshAfterVfsChange(FileTab tab) {
        refreshAfterVfsChange(tab, null);
    }

    public static void refreshAfterVfsChange(FileTab tab, @Nullable String selectName) {
        tab.invalidateFreeSpaceCache();
        VirtualFileSystem vfs = tab.getCurrentVfs();
        if (vfs == null) {
            FileTabNavigationUtil.navigateTo(tab, tab.getCurrentPath(), selectName, true);
            return;
        }

        String relative = VfsPaths.vfsRelativePath(vfs.getRoot(), tab.getCurrentPath());
        Lock lock = tab.getVfsWriteLock();
        lock.lock();
        try {
            vfs.flush();
            writeBackNestedArchivesLocked(tab);
        }
        catch (IOException e) {
            // A failed repack means the user's edit did not reach the archive; say so rather than
            // silently re-listing the pre-edit contents.
            tab.fileErrorNotification("Failed to write changes back: " + FileUtils.fileErrorMessage(e));
            return;
        }
        finally {
            lock.unlock();
        }

        Path newPath = relative.isEmpty() ? vfs.getRoot() : vfs.getRoot().resolve(relative);
        FileTabNavigationUtil.navigateTo(tab, newPath, selectName, true);
    }

    private static void deleteTempQuietly(@Nullable Path tempPath) {
        if (tempPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempPath);
            Path parent = tempPath.getParent();
            if (parent != null) {
                Files.deleteIfExists(parent);
            }
        }
        catch (Exception e) {
            LOG.debug("Failed to clean up nested-archive temp file " + tempPath, e);
        }
    }

    public record VfsStackPrefixes(String separator, List<String> prefixes) {
    }

    /** The user-facing path: the archive chain when inside one, the real path otherwise. */
    public static String getDisplayPath(FileTab tab) {
        VirtualFileSystem vfs = tab.getCurrentVfs();
        if (vfs == null || tab.getVfsStack().isEmpty()) {
            return tab.getCurrentPath().toString();
        }

        VfsStackPrefixes prefixes = buildVfsStackPrefixes(tab);
        StringBuilder sb = new StringBuilder(prefixes.prefixes().get(prefixes.prefixes().size() - 1));
        if (!vfs.isRoot(tab.getCurrentPath())) {
            String relative = VfsPaths.vfsRelativePath(vfs.getRoot(), tab.getCurrentPath());
            sb.append(prefixes.separator())
                .append(StringUtil.trimLeading(relative, '/').replace("/", prefixes.separator()));
        }
        return sb.toString();
    }

    private static void closeQuietly(VfsStackEntry entry) {
        try {
            entry.getVfs().close();
        }
        catch (Exception e) {
            LOG.warn("Failed to close VFS " + entry.getVfs().getArchivePath(), e);
        }
        entry.cleanupTempFile();
    }
}
