package consulo.turtlecommander.action;

import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.FileManagerTab;
import consulo.turtlecommander.vfs.VirtualFileSystemRegistry;
import consulo.ui.ex.action.AnActionEvent;
import org.jspecify.annotations.Nullable;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * An action on the file context menu, targeting the right-clicked entry and its tab.
 *
 * <p>Entries the action can never apply to are hidden rather than merely disabled: a menu of greyed
 * "Extract" items on a directory is noise. Actions that apply to everything but may be unavailable
 * right now — Paste with an empty buffer — stay visible and disable instead.
 */
public abstract class FileContextAction extends EdtAction {
    private final @Nullable BiPredicate<FileManagerTab, FileEntry> myApplicable;
    private final boolean myHideWhenInapplicable;

    /**
     * @param applicable          when the action makes sense for the clicked entry; null means always
     * @param hideWhenInapplicable hide rather than disable when {@code applicable} is false
     */
    protected FileContextAction(LocalizeValue text, @Nullable String defaultShortcut,
                                @Nullable BiPredicate<FileManagerTab, FileEntry> applicable,
                                boolean hideWhenInapplicable) {
        super(text);
        setDefaultShortcut(defaultShortcut);
        myApplicable = applicable;
        myHideWhenInapplicable = hideWhenInapplicable;
    }

    @Override
    public void update(AnActionEvent e) {
        FileManagerTab tab = FileContextMenuState.getClickedTab(e);
        FileEntry entry = FileContextMenuState.getClickedEntry(e);
        boolean applicable = tab != null && entry != null
            && (myApplicable == null || myApplicable.test(tab, entry));

        if (myHideWhenInapplicable) {
            e.getPresentation().setEnabledAndVisible(applicable);
        }
        else {
            e.getPresentation().setEnabled(applicable);
        }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileManagerTab tab = FileContextMenuState.getClickedTab(e);
        if (tab != null) {
            performOnTab(tab, e);
        }
    }

    protected abstract void performOnTab(FileManagerTab tab, AnActionEvent e);

    /** Base for the context actions that just delegate to a tab operation. */
    public abstract static class Simple extends FileContextAction {
        private final Consumer<FileManagerTab> myOperation;

        protected Simple(LocalizeValue text, @Nullable String defaultShortcut,
                         @Nullable BiPredicate<FileManagerTab, FileEntry> applicable,
                         boolean hideWhenInapplicable, Consumer<FileManagerTab> operation) {
            super(text, defaultShortcut, applicable, hideWhenInapplicable);
            myOperation = operation;
        }

        @Override
        protected void performOnTab(FileManagerTab tab, AnActionEvent e) {
            myOperation.accept(tab);
        }
    }

    // --- Shared applicability predicates ------------------------------------------------------------

    /** A real entry, i.e. not the ".." parent link. */
    public static boolean isRealEntry(FileManagerTab tab, FileEntry entry) {
        return !entry.parentLink();
    }

    public static boolean isDirectory(FileManagerTab tab, FileEntry entry) {
        return entry.directory() && !entry.parentLink();
    }

    public static boolean isFile(FileManagerTab tab, FileEntry entry) {
        return !entry.directory() && !entry.parentLink();
    }

    public static boolean isArchive(FileManagerTab tab, FileEntry entry) {
        return !entry.directory()
            && VirtualFileSystemRegistry.supportsByExtension(entry.name());
    }

    /** A real file on disk — split, combine and linking are meaningless inside an archive. */
    public static boolean isFileOutsideArchive(FileManagerTab tab, FileEntry entry) {
        return isFile(tab, entry) && !tab.isInsideArchive();
    }

    public static boolean hasSelection(FileManagerTab tab, FileEntry entry) {
        return !tab.getSelectedEntries().isEmpty();
    }
}
