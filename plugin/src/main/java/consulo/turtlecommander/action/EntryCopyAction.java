package consulo.turtlecommander.action;

import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabVfsUtil;
import consulo.ui.ex.CopyPasteManager;
import consulo.ui.ex.action.AnActionEvent;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Bases for the "Copy as" menus, shared between the file panel and the search results — the two
 * differ only in where the target entry comes from.
 */
public abstract class EntryCopyAction extends EdtAction {
    protected EntryCopyAction(LocalizeValue text) {
        super(text);
    }

    /** The entry this action copies from, or null when there is none. */
    protected abstract @Nullable FileEntry entry(AnActionEvent e);

    /** The owning tab, when archive-aware path resolution is possible; null for search results. */
    protected @Nullable FileTab tab(AnActionEvent e) {
        return null;
    }

    @Override
    public void update(AnActionEvent e) {
        FileEntry entry = entry(e);
        e.getPresentation().setEnabled(entry != null && !entry.parentLink());
    }

    /**
     * The user-facing full path. Inside an archive {@code entry.path()} points at a temp-extracted
     * file, so the tab's display path joined with the entry name is what the user actually means.
     */
    protected static String fullPathOf(FileEntry entry, @Nullable FileTab tab) {
        if (tab == null || !tab.isInsideArchive()) {
            return entry.path().toString();
        }
        String displayPath = FileTabVfsUtil.getDisplayPath(tab);
        String separator = displayPath.contains("\\") ? "\\" : "/";
        return displayPath + separator + entry.name();
    }

    protected static String parentPathOf(FileEntry entry, @Nullable FileTab tab) {
        if (tab != null && tab.isInsideArchive()) {
            return FileTabVfsUtil.getDisplayPath(tab);
        }
        Path parent = entry.path().getParent();
        return parent != null ? parent.toString() : "";
    }

    protected static void copy(String text) {
        CopyPasteManager.getInstance().setText(text);
    }

    /** Copies the entry's name. The label follows the entry so a directory doesn't say "Filename". */
    public abstract static class Name extends EntryCopyAction {
        protected Name() {
            super(LocalizeValue.localizeTODO("Filename"));
        }

        @Override
        public void update(AnActionEvent e) {
            super.update(e);
            FileEntry entry = entry(e);
            e.getPresentation().setText(LocalizeValue.localizeTODO(
                entry != null && entry.directory() ? "Directory Name" : "Filename"));
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            FileEntry entry = entry(e);
            if (entry != null) {
                copy(entry.name());
            }
        }
    }

    public abstract static class FullPath extends EntryCopyAction {
        protected FullPath() {
            super(LocalizeValue.localizeTODO("Full Path"));
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            FileEntry entry = entry(e);
            if (entry != null) {
                copy(fullPathOf(entry, tab(e)));
            }
        }
    }

    public abstract static class ParentPath extends EntryCopyAction {
        protected ParentPath() {
            super(LocalizeValue.localizeTODO("Parent Path"));
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            FileEntry entry = entry(e);
            if (entry != null) {
                copy(parentPathOf(entry, tab(e)));
            }
        }
    }

    /**
     * Copies one metadata attribute. Disabled when the entry doesn't carry it — no creation time
     * recorded, a blank owner on a platform that doesn't report one — so the menu never copies an
     * empty string.
     */
    public abstract static class Attribute extends EntryCopyAction {
        private final Function<FileEntry, @Nullable String> myValue;

        protected Attribute(LocalizeValue text, Function<FileEntry, @Nullable String> value) {
            super(text);
            myValue = value;
        }

        @Override
        public void update(AnActionEvent e) {
            FileEntry entry = entry(e);
            String value = entry != null ? myValue.apply(entry) : null;
            e.getPresentation().setEnabled(entry != null && !entry.parentLink()
                && value != null && !value.isBlank());
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            FileEntry entry = entry(e);
            String value = entry != null ? myValue.apply(entry) : null;
            if (value != null && !value.isBlank()) {
                copy(value);
            }
        }
    }

    /** Copies a whole set of entries as CSV or JSON. */
    public abstract static class Export extends EntryCopyAction {
        private final Function<List<FileEntry>, String> myFormat;

        protected Export(LocalizeValue text, Function<List<FileEntry>, String> format) {
            super(text);
            myFormat = format;
        }

        @Override
        protected @Nullable FileEntry entry(AnActionEvent e) {
            return null;
        }

        /** The entries to export. */
        protected abstract List<FileEntry> targets(AnActionEvent e);

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(!targets(e).isEmpty());
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            List<FileEntry> entries = targets(e);
            if (!entries.isEmpty()) {
                copy(myFormat.apply(entries));
            }
        }
    }

    /**
     * What the user means by "these entries": the current selection, or the right-clicked row when
     * nothing is selected. So the action works both when right-clicking an unselected row and when
     * right-clicking inside a multi-selection.
     */
    protected static List<FileEntry> withFallbackToClicked(List<FileEntry> selected,
                                                           @Nullable FileEntry clicked) {
        List<FileEntry> targets = new ArrayList<>();
        for (FileEntry entry : selected) {
            if (!entry.parentLink()) {
                targets.add(entry);
            }
        }
        if (targets.isEmpty() && clicked != null && !clicked.parentLink()) {
            targets.add(clicked);
        }
        return targets;
    }
}
