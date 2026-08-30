package consulo.turtlecommander.ui;

import consulo.application.util.SystemInfo;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.operation.CdCommand;
import consulo.turtlecommander.service.FileOperationService;
import consulo.turtlecommander.service.TabState;
import consulo.turtlecommander.service.ThumbnailCache;
import consulo.turtlecommander.util.FileUtils;
import consulo.turtlecommander.util.UiTask;
import consulo.turtlecommander.vfs.VfsPaths;
import consulo.turtlecommander.vfs.VirtualFileSystem;
import consulo.ui.ex.awt.JBList;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import javax.swing.DefaultListModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * Listing a directory into the tab and everything that follows from it: path bar, view models,
 * cursor restoration, and the archive-aware Up / Root / {@code cd} entry points.
 *
 * <p>{@link #navigateTo} does its I/O on the calling thread and must therefore be called off the UI
 * thread; the model and selection updates it performs are pushed back onto the UI thread. Callers
 * that are already on the UI thread should wrap it in {@link UiTask#background}.
 */
public final class FileTabNavigationUtil {
    private FileTabNavigationUtil() {
    }

    public static void navigateTo(FileTab tab, Path path) {
        navigateTo(tab, path, null, true);
    }

    public static void navigateTo(FileTab tab, Path path, @Nullable String selectName, boolean requestFocus) {
        VirtualFileSystem vfs = tab.getCurrentVfs();
        List<FileEntry> entries;
        try {
            entries = listEntries(tab, vfs, path);
        }
        catch (Exception e) {
            if (vfs == null && navigateToFallback(tab, path, requestFocus)) {
                return;
            }
            UiTask.ui(() -> tab.fileErrorNotification("Cannot list directory: " + FileUtils.fileErrorMessage(e)));
            return;
        }

        List<FileEntry> listing = entries;
        UiTask.ui(() -> applyListing(tab, vfs, path, listing, selectName, requestFocus));
    }

    private static List<FileEntry> listEntries(FileTab tab, @Nullable VirtualFileSystem vfs, Path path)
        throws Exception {
        if (vfs != null) {
            return vfs.listFiles(path);
        }
        // A recursive files-only listing only makes sense on the real filesystem.
        return tab.isShowAllNestedFiles()
            ? tab.getFileOps().listAllNestedFiles(path)
            : tab.getFileOps().listFiles(path);
    }

    /**
     * Recovers from a failed listing on the real filesystem: land on the nearest surviving ancestor,
     * or on the home directory when the path's root itself is gone (a tab restored on an unplugged
     * drive). Returns false when neither is reachable, leaving the caller to report the error.
     */
    private static boolean navigateToFallback(FileTab tab, Path path, boolean requestFocus) {
        Path ancestor = NavigationFallbackUtil.nearestExistingAncestor(path);
        Path fallback = ancestor != null ? ancestor : NavigationFallbackUtil.homeDirectoryFallback(path);
        if (fallback == null) {
            return false;
        }
        if (ancestor == null) {
            UiTask.ui(() -> tab.fileErrorNotification(
                path + " is not available \u2014 opened the home directory instead"));
        }
        navigateTo(tab, fallback, null, requestFocus);
        return true;
    }

    private static void applyListing(FileTab tab, @Nullable VirtualFileSystem vfs, Path path,
                                     List<FileEntry> entries, @Nullable String selectName,
                                     boolean requestFocus) {
        int selectedRow = tab.getTable().getSelectedRow();
        if (selectedRow >= 0) {
            tab.getCursorPositions().put(tab.getCurrentPath(), selectedRow);
        }
        // Only save column state while the user has actually been in the table: saving from
        // LIST/TREE/THUMBNAIL would capture stale defaults and mask the "no state yet" signal the
        // view switch relies on.
        if (tab.isInitialized() && tab.getViewMode() == ViewMode.TABLE) {
            tab.saveColumnState();
        }

        tab.setCurrentPath(path);
        tab.getPathField().setText(pathBarText(tab, vfs, path));

        tab.setAllEntries(entries);
        tab.setFilteredEntries(entries);
        tab.getDirectorySizes().clear();
        // Drop marks on directory change so they don't accumulate across navigations. Path-keyed
        // marks would technically survive, but users expect a fresh slate after a cd.
        FileTabSelectionUtil.clearAllToggledMarks(tab);
        tab.hideQuickFilterIfActive();

        FileTabViewUtil.setEntriesInAllViews(tab, entries);
        if (tab.getViewMode() == ViewMode.TREE && !tab.isShowAllNestedFiles()) {
            FileTabViewUtil.rebuildFullTree(tab);
        }

        tab.updateStatusBar();
        tab.notifyDirectoryChanged();

        TabState pending = tab.takePendingTabState();
        if (pending != null) {
            tab.restoreTabState(pending);
        }
        else if (tab.isInitialized()) {
            tab.restoreColumnState(path);
        }

        if (vfs == null) {
            // Apply any roots update parked while this tab was inside an archive.
            tab.getDriveCombo().flushPendingLater(path);
            tab.getDriveCombo().syncSelection(path);
        }

        restoreCursor(tab, path, selectName);
        tab.setInitialized(true);

        if (requestFocus) {
            tab.focusActiveView();
        }
    }

    /** The breadcrumb text: the archive chain when inside one, the plain path otherwise. */
    private static String pathBarText(FileTab tab, @Nullable VirtualFileSystem vfs, Path path) {
        if (vfs == null || tab.getVfsStack().isEmpty()) {
            return path.toString();
        }
        FileTabVfsUtil.VfsStackPrefixes prefixes = FileTabVfsUtil.buildVfsStackPrefixes(tab);
        StringBuilder sb = new StringBuilder(prefixes.prefixes().get(prefixes.prefixes().size() - 1));
        if (!vfs.isRoot(path)) {
            String relative = VfsPaths.vfsRelativePath(vfs.getRoot(), path);
            sb.append(prefixes.separator())
                .append(StringUtil.trimLeading(relative, '/').replace("/", prefixes.separator()));
        }
        return sb.toString();
    }

    private static void restoreCursor(FileTab tab, Path path, @Nullable String selectName) {
        Integer savedCursor = tab.getCursorPositions().get(path);

        int tableTarget = SelectionTargetUtil.resolveTargetIndex(tab.getTable().getRowCount(),
            viewRow -> {
                FileEntry entry = tab.getTableModel()
                    .getEntryAt(tab.getTable().convertRowIndexToModel(viewRow));
                return entry != null ? entry.name() : null;
            }, selectName, savedCursor);
        if (tableTarget != SelectionTargetUtil.NONE) {
            tab.getTable().setRowSelectionInterval(tableTarget, tableTarget);
            tab.getTable().scrollRectToVisible(tab.getTable().getCellRect(tableTarget, 0, true));
        }

        selectInList(tab.getList(), tab.getListModel(), selectName, savedCursor);
        selectInList(tab.getThumbnailList(), tab.getThumbnailListModel(), selectName, savedCursor);
    }

    private static void selectInList(JBList<FileEntry> list, DefaultListModel<FileEntry> model,
                                     @Nullable String selectName, @Nullable Integer savedCursor) {
        int target = SelectionTargetUtil.resolveTargetIndex(model.size(),
            index -> model.getElementAt(index).name(), selectName, savedCursor);
        if (target == SelectionTargetUtil.NONE) {
            return;
        }
        list.setSelectedIndex(target);
        list.ensureIndexIsVisible(target);
    }

    /** Re-lists the current directory, flushing the archive stack first when inside one. */
    public static void refresh(FileTab tab, boolean requestFocus) {
        tab.invalidateFreeSpaceCache();
        ThumbnailCache.getInstance().evictDirectory(tab.getCurrentPath());

        VirtualFileSystem vfs = tab.getCurrentVfs();
        if (vfs == null) {
            tab.getFileOps().invalidateListingCache(tab.getCurrentPath());
            UiTask.background(() -> navigateTo(tab, tab.getCurrentPath(), null, requestFocus));
            return;
        }

        String relative = VfsPaths.vfsRelativePath(vfs.getRoot(), tab.getCurrentPath());
        UiTask.background(() -> {
            Lock lock = tab.getVfsWriteLock();
            lock.lock();
            try {
                vfs.flush();
                FileTabVfsUtil.writeBackNestedArchivesLocked(tab);
            }
            catch (Exception e) {
                UiTask.ui(() -> tab.fileErrorNotification(
                    "Failed to write changes back: " + FileUtils.fileErrorMessage(e)));
                return;
            }
            finally {
                lock.unlock();
            }
            Path newPath = relative.isEmpty() ? vfs.getRoot() : vfs.getRoot().resolve(relative);
            navigateTo(tab, newPath, null, requestFocus);
        });
    }

    /**
     * Re-lists only if the listing cache has gone stale, so externally-modified directories stop
     * showing ghost rows without forcing a re-render on every tab switch.
     *
     * <p>Skipped inside an archive: the filesystem's own state is authoritative there, and the
     * archive file's real mtime says nothing about the virtual directory listing.
     */
    public static void revalidateIfStale(FileTab tab) {
        if (tab.getCurrentVfs() != null) {
            return;
        }
        FileOperationService fileOps = tab.getFileOps();
        if (fileOps.isListingCacheFresh(tab.getCurrentPath())) {
            return;
        }
        UiTask.background(() -> navigateTo(tab, tab.getCurrentPath(), null, false));
    }

    public static void openSelectedEntry(FileTab tab) {
        FileEntry entry = FileTabSelectionUtil.getSelectedEntry(tab);
        if (entry == null) {
            return;
        }
        if (entry.parentLink()) {
            goUp(tab);
            return;
        }
        if (entry.directory()) {
            UiTask.background(() -> navigateTo(tab, entry.path()));
            return;
        }
        // A symbolic link can point at a directory without having been classified as one — the
        // listing's follow-read can fail, as reported on macOS for redirected home folders that
        // link into cloud storage. Resolve the target off the UI thread and enter it when it really
        // is a directory; otherwise fall through to the normal file/archive handling.
        if (entry.symbolicLink()) {
            UiTask.background(() -> {
                if (Files.isDirectory(entry.path())) {
                    navigateTo(tab, entry.path());
                }
                else {
                    UiTask.ui(() -> openNonDirectoryEntry(tab, entry));
                }
            });
            return;
        }
        openNonDirectoryEntry(tab, entry);
    }

    private static void openNonDirectoryEntry(FileTab tab, FileEntry entry) {
        if (!entry.namedPipe() && tab.isEntryBrowsableArchive(entry)) {
            FileTabVfsUtil.enterVfs(tab, entry);
        }
        else {
            FileTabOperationUtil.openFile(tab, entry);
        }
    }

    public static void goUp(FileTab tab) {
        VirtualFileSystem vfs = tab.getCurrentVfs();
        if (vfs != null && vfs.isRoot(tab.getCurrentPath())) {
            FileTabVfsUtil.exitVfs(tab);
            return;
        }
        Path parent = tab.getCurrentPath().getParent();
        if (parent != null) {
            UiTask.background(() -> navigateTo(tab, parent));
        }
    }

    public static void goToRoot(FileTab tab) {
        VirtualFileSystem vfs = tab.getCurrentVfs();
        Path root = vfs != null ? vfs.getRoot() : tab.getCurrentPath().getRoot();
        if (root != null && !root.equals(tab.getCurrentPath())) {
            UiTask.background(() -> navigateTo(tab, root));
        }
    }

    /**
     * Handles a {@code cd} typed in the command bar. {@code rawArg} is the already-unquoted target
     * ("" navigates home). A no-op inside an archive — the command bar blocks commands there.
     *
     * @param onSuccess runs on the UI thread after a successful move, so the caller can refresh
     *                  its prompt
     */
    public static void changeDirectoryFromCommand(FileTab tab, String rawArg, Runnable onSuccess) {
        if (tab.getCurrentVfs() != null) {
            return;
        }
        Path home = Path.of(System.getProperty("user.home"));
        Path target = CdCommand.resolveTarget(rawArg, tab.getCurrentPath(), home, SystemInfo.isWindows);
        if (target == null) {
            tab.fileErrorNotification("cd: invalid path: " + rawArg);
            return;
        }
        UiTask.background(() -> {
            if (Files.isDirectory(target)) {
                navigateTo(tab, target);
                UiTask.ui(onSuccess);
            }
            else {
                UiTask.ui(() -> tab.fileErrorNotification("cd: no such directory: " + target));
            }
        });
    }
}
