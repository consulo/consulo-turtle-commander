package consulo.turtlecommander.ui;

import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.service.FileOperationService;
import consulo.turtlecommander.vfs.VfsStackEntry;
import consulo.turtlecommander.vfs.VirtualFileSystem;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * What the actions and file operations need from a tab, whichever UI draws it: where it is, what is selected in it,
 * the tab of the other pane, and a few things to do to it. The Swing {@link FileTab} and the unified
 * {@link UnifiedFileTab} both are one.
 */
public interface FileManagerTab {
    Project getProject();

    /** The UI the tab lives in - what work finished off the UI thread hands its results back through. */
    UIAccess getUIAccess();

    FileOperationService getFileOps();

    Path getCurrentPath();

    /** The archive the tab is inside of, or null on the plain file system. */
    @Nullable VirtualFileSystem getCurrentVfs();

    boolean isInsideArchive();

    /** The real file system location - the archive file itself while inside one. */
    Path getRealFilesystemPath();

    List<VfsStackEntry> getVfsStack();

    ReentrantLock getVfsWriteLock();

    @Nullable Path getOtherPanelPath();

    @Nullable FileManagerTab getOtherPanelTab();

    @Nullable String getOtherPanelDisplayPath();

    void fileErrorNotification(String content);

    void invalidateFreeSpaceCache();

    /** Lists the other pane's directory again, after an operation which wrote into it. */
    default void refreshOtherPanel() {
        FileManagerTab other = getOtherPanelTab();
        if (other != null) {
            other.refresh();
        }
    }

    /** The entries an operation acts on: the marked ones, or the one under the cursor; never the ".." row. */
    List<FileEntry> getSelectedEntries();

    /** The entry under the cursor, or null; may be the ".." row. */
    @Nullable FileEntry getSelectedEntry();

    /** Every entry the view shows now, after the quick filter. */
    List<FileEntry> getDisplayedEntries();

    Set<Path> getMarkedPaths();

    /** Lists the current directory again, off the UI thread. */
    void refresh();

    /** Lists {@code directory} off the UI thread and shows it. */
    default void navigateTo(Path directory) {
        navigateTo(directory, null);
    }

    /** Lists {@code directory} off the UI thread and shows it with the entry named {@code selectName} selected. */
    void navigateTo(Path directory, @Nullable String selectName);

    @RequiredUIAccess
    void focusActiveView();

    boolean hasAnyViewFocus();

    @RequiredUIAccess
    void showQuickFilter();

    /** Starts renaming the entry under the cursor - in place where the view can, otherwise in a dialog. */
    @RequiredUIAccess
    void startRename();
    /** The location as the path bar shows it - inside an archive, the archive's path followed by the inner one. */
    String getDisplayPath();

    void goUp();

    void goToRoot();

    /** Opens the entry under the cursor: enters a directory or an archive, opens a file. */
    @RequiredUIAccess
    void openSelectedEntry();

    @RequiredUIAccess
    void selectFirstRow();

    @RequiredUIAccess
    void selectLastRow();

    @RequiredUIAccess
    void selectAllEntries();

    @RequiredUIAccess
    void unselectAllEntries();

    @RequiredUIAccess
    void invertSelection();

    /** Marks or unmarks the entry under the cursor. */
    @RequiredUIAccess
    void toggleSelection();

    /** Marks or unmarks the entry under the cursor and moves the cursor down, like Insert in a commander. */
    @RequiredUIAccess
    void toggleSelectionAndMoveDown();

    @RequiredUIAccess
    void saveSelection();

    @RequiredUIAccess
    void restoreSavedSelection();

    /** Marks ({@code select}) or unmarks the entries whose names match the glob {@code mask}. */
    @RequiredUIAccess
    void markByMask(String mask, boolean select, boolean includeDirectories);

    /** Marks ({@code select}) or unmarks the entries with the extension of the one under the cursor. */
    @RequiredUIAccess
    void markSameExtension(boolean select);

    @RequiredUIAccess
    void showContextMenu();
}
