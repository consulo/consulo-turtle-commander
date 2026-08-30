package consulo.turtlecommander.ui;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.util.UiTask;
import consulo.ui.ex.awt.JBList;
import org.jspecify.annotations.Nullable;

import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Cursor, multi-selection and persistent marks across the four views. */
public final class FileTabSelectionUtil {
    private FileTabSelectionUtil() {
    }

    /**
     * The entry under the cursor.
     *
     * <p>Deliberately reads the <em>lead</em> selection index rather than the minimum selected one.
     * With a multi-selection — Select All, a Shift-click range, or restored marks — {@code
     * selectedRow}/{@code selectedValue} return the topmost marked row, not the focused one, which
     * would make Enter open the first file instead of the one under the cursor.
     */
    public static @Nullable FileEntry getSelectedEntry(FileTab tab) {
        switch (tab.getViewMode()) {
            case LIST -> {
                return leadOf(tab.getList(), tab.getListModel());
            }
            case THUMBNAIL -> {
                return leadOf(tab.getThumbnailList(), tab.getThumbnailListModel());
            }
            case TREE -> {
                TreePath leadPath = tab.getTree().getLeadSelectionPath();
                if (leadPath == null) {
                    leadPath = tab.getTree().getSelectionPath();
                }
                return entryOf(leadPath);
            }
            default -> {
                int leadRow = tab.getTable().getSelectionModel().getLeadSelectionIndex();
                int viewRow = leadRow >= 0 && leadRow < tab.getTable().getRowCount()
                    ? leadRow
                    : tab.getTable().getSelectedRow();
                if (viewRow < 0) {
                    return null;
                }
                return tab.getTableModel().getEntryAt(tab.getTable().convertRowIndexToModel(viewRow));
            }
        }
    }

    /** Every selected entry, excluding the parent ".." row. */
    public static List<FileEntry> getSelectedEntries(FileTab tab) {
        List<FileEntry> result = new ArrayList<>();

        switch (tab.getViewMode()) {
            case LIST -> addNonParent(result, tab.getList().getSelectedValuesList());
            case THUMBNAIL -> addNonParent(result, tab.getThumbnailList().getSelectedValuesList());
            case TREE -> {
                TreePath[] paths = tab.getTree().getSelectionPaths();
                if (paths != null) {
                    for (TreePath path : paths) {
                        FileEntry entry = entryOf(path);
                        if (entry != null && !entry.parentLink()) {
                            result.add(entry);
                        }
                    }
                }
            }
            default -> {
                for (int viewRow : tab.getTable().getSelectedRows()) {
                    FileEntry entry = tab.getTableModel()
                        .getEntryAt(tab.getTable().convertRowIndexToModel(viewRow));
                    if (entry != null && !entry.parentLink()) {
                        result.add(entry);
                    }
                }
            }
        }
        return result;
    }

    /** Entries currently displayed in the active view, excluding the parent row. */
    public static List<FileEntry> getDisplayedEntries(FileTab tab) {
        List<FileEntry> result = new ArrayList<>();
        for (FileEntry entry : tab.getFilteredEntries()) {
            if (!entry.parentLink()) {
                result.add(entry);
            }
        }
        return result;
    }

    public static void toggleSelection(FileTab tab) {
        toggleSelectionInternal(tab, false);
    }

    public static void toggleSelectionAndMoveDown(FileTab tab) {
        toggleSelectionInternal(tab, true);
    }

    private static void toggleSelectionInternal(FileTab tab, boolean moveCursorDown) {
        tab.setInsideToggle(true);
        try {
            switch (tab.getViewMode()) {
                case TABLE -> {
                    int row = tab.getTable().getSelectionModel().getLeadSelectionIndex();
                    if (row < 0) {
                        return;
                    }
                    toggleMarkForEntry(tab,
                        tab.getTableModel().getEntryAt(tab.getTable().convertRowIndexToModel(row)));

                    int cursorRow = moveCursorDown && row + 1 < tab.getTable().getRowCount() ? row + 1 : row;
                    applyMarksToTable(tab);
                    moveTableLead(tab, cursorRow);
                    if (moveCursorDown) {
                        tab.getTable().scrollRectToVisible(tab.getTable().getCellRect(cursorRow, 0, true));
                    }
                }
                case LIST -> toggleListSelection(tab, tab.getList(), tab.getListModel(), moveCursorDown);
                case THUMBNAIL ->
                    toggleListSelection(tab, tab.getThumbnailList(), tab.getThumbnailListModel(), moveCursorDown);
                case TREE -> {
                    int leadRow = tab.getTree().getLeadSelectionRow();
                    if (leadRow < 0) {
                        return;
                    }
                    toggleMarkForEntry(tab, entryOf(tab.getTree().getPathForRow(leadRow)));

                    int cursorRow = moveCursorDown && leadRow + 1 < tab.getTree().getRowCount()
                        ? leadRow + 1
                        : leadRow;
                    applyMarksToTree(tab);
                    if (moveCursorDown) {
                        tab.getTree().scrollRowToVisible(cursorRow);
                    }
                }
            }
        }
        finally {
            tab.setInsideToggle(false);
        }
    }

    private static void toggleListSelection(FileTab tab, JBList<FileEntry> list,
                                            DefaultListModel<FileEntry> model, boolean moveCursorDown) {
        int index = list.getSelectionModel().getLeadSelectionIndex();
        if (index < 0 || index >= model.size()) {
            return;
        }

        toggleMarkForEntry(tab, model.getElementAt(index));
        int cursorIndex = moveCursorDown && index + 1 < model.size() ? index + 1 : index;
        applyMarksToList(tab, list, model);

        if (list.getSelectionModel() instanceof DefaultListSelectionModel lsm) {
            lsm.moveLeadSelectionIndex(cursorIndex);
        }
        if (moveCursorDown) {
            list.ensureIndexIsVisible(cursorIndex);
        }
    }

    private static void toggleMarkForEntry(FileTab tab, @Nullable FileEntry entry) {
        if (entry == null || entry.parentLink()) {
            return;
        }
        Set<Path> marked = tab.getMarkedPaths();
        if (marked.contains(entry.path())) {
            marked.remove(entry.path());
        }
        else {
            marked.add(entry.path());
        }
    }

    // --- Applying marks back onto the views -------------------------------------------------------

    public static void applyMarksToTable(FileTab tab) {
        tab.getTable().clearSelection();
        for (int viewRow = 0; viewRow < tab.getTable().getRowCount(); viewRow++) {
            FileEntry entry = tab.getTableModel().getEntryAt(tab.getTable().convertRowIndexToModel(viewRow));
            if (entry != null && tab.getMarkedPaths().contains(entry.path())) {
                tab.getTable().addRowSelectionInterval(viewRow, viewRow);
            }
        }
    }

    public static void applyMarksToList(FileTab tab, JBList<FileEntry> list, DefaultListModel<FileEntry> model) {
        list.clearSelection();
        for (int i = 0; i < model.size(); i++) {
            if (tab.getMarkedPaths().contains(model.getElementAt(i).path())) {
                list.addSelectionInterval(i, i);
            }
        }
    }

    public static void applyMarksToTree(FileTab tab) {
        tab.getTree().clearSelection();
        for (int row = 0; row < tab.getTree().getRowCount(); row++) {
            FileEntry entry = entryOf(tab.getTree().getPathForRow(row));
            if (entry != null && tab.getMarkedPaths().contains(entry.path())) {
                tab.getTree().addSelectionRow(row);
            }
        }
    }

    /** Re-applies marks to whichever view is active, guarding against re-entrant listeners. */
    public static void applyMarksForCurrentView(FileTab tab) {
        tab.setInsideRestore(true);
        try {
            switch (tab.getViewMode()) {
                case TABLE -> applyMarksToTable(tab);
                case LIST -> applyMarksToList(tab, tab.getList(), tab.getListModel());
                case THUMBNAIL -> applyMarksToList(tab, tab.getThumbnailList(), tab.getThumbnailListModel());
                case TREE -> applyMarksToTree(tab);
            }
        }
        finally {
            tab.setInsideRestore(false);
        }
    }

    /**
     * Drops all marks. Called on directory change: path-keyed marks would technically survive, but
     * users expect a fresh slate after navigating somewhere new.
     */
    public static void clearAllToggledMarks(FileTab tab) {
        tab.getMarkedPaths().clear();
    }

    // --- Bulk marking --------------------------------------------------------------------------------

    public static void selectAllEntries(FileTab tab) {
        for (FileEntry entry : getDisplayedEntries(tab)) {
            mark(tab, entry);
        }
        applyMarksForCurrentView(tab);
    }

    public static void unselectAllEntries(FileTab tab) {
        if (tab.getMarkedPaths().isEmpty()) {
            return;
        }
        tab.getMarkedPaths().clear();
        applyMarksForCurrentView(tab);
    }

    public static void invertSelectionEntries(FileTab tab) {
        for (FileEntry entry : getDisplayedEntries(tab)) {
            if (tab.getMarkedPaths().contains(entry.path())) {
                tab.getMarkedPaths().remove(entry.path());
            }
            else {
                mark(tab, entry);
            }
        }
        applyMarksForCurrentView(tab);
    }

    /** Marks every displayed file sharing the cursor entry's extension; directories are skipped. */
    public static void selectSameExtension(FileTab tab) {
        applyToSameExtension(tab, true);
    }

    public static void unselectSameExtension(FileTab tab) {
        applyToSameExtension(tab, false);
    }

    private static void applyToSameExtension(FileTab tab, boolean mark) {
        FileEntry focus = getSelectedEntry(tab);
        if (focus == null || focus.parentLink()) {
            return;
        }
        String extension = extensionOf(focus);
        if (extension == null) {
            return;
        }
        for (FileEntry entry : getDisplayedEntries(tab)) {
            if (entry.directory() || !extension.equals(extensionOf(entry))) {
                continue;
            }
            if (mark) {
                tab.getMarkedPaths().add(entry.path());
            }
            else {
                tab.getMarkedPaths().remove(entry.path());
            }
        }
        applyMarksForCurrentView(tab);
    }

    /**
     * Marks or unmarks by a Total Commander-style glob mask: comma- or semicolon-separated tokens,
     * each matched against the entry's name. An empty mask matches nothing. Directories are excluded
     * unless asked for, matching TC's files-only default.
     */
    public static void selectByMask(FileTab tab, String mask, boolean includeDirectories) {
        applyMask(tab, mask, includeDirectories, true);
    }

    public static void unselectByMask(FileTab tab, String mask, boolean includeDirectories) {
        applyMask(tab, mask, includeDirectories, false);
    }

    private static void applyMask(FileTab tab, String mask, boolean includeDirectories, boolean mark) {
        List<PathMatcher> patterns = parseSelectionMaskPatterns(mask);
        if (patterns.isEmpty()) {
            return;
        }
        for (FileEntry entry : getDisplayedEntries(tab)) {
            if ((!includeDirectories && entry.directory()) || !matchesMask(entry.name(), patterns)) {
                continue;
            }
            if (mark) {
                mark(tab, entry);
            }
            else {
                tab.getMarkedPaths().remove(entry.path());
            }
        }
        applyMarksForCurrentView(tab);
    }

    public static List<PathMatcher> parseSelectionMaskPatterns(String mask) {
        List<PathMatcher> patterns = new ArrayList<>();
        for (String token : mask.split("[,;]")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                patterns.add(FileSystems.getDefault().getPathMatcher("glob:" + trimmed));
            }
            catch (Exception e) {
                // An unparseable token contributes nothing rather than failing the whole mask.
            }
        }
        return patterns;
    }

    private static boolean matchesMask(String name, List<PathMatcher> patterns) {
        Path asPath = Path.of(name);
        for (PathMatcher pattern : patterns) {
            if (pattern.matches(asPath)) {
                return true;
            }
        }
        return false;
    }

    /** Marking a directory also kicks off its recursive size, which the status bar sums. */
    private static void mark(FileTab tab, FileEntry entry) {
        tab.getMarkedPaths().add(entry.path());
        if (entry.directory()) {
            calculateDirectorySize(tab, entry);
        }
    }

    /**
     * The extension in Total Commander's sense: null for a directory, and empty for a name with no
     * dot or a leading-dot name like {@code .gitignore}.
     */
    public static @Nullable String extensionOf(FileEntry entry) {
        if (entry.directory()) {
            return null;
        }
        int dot = entry.name().lastIndexOf('.');
        return dot > 0 && dot < entry.name().length() - 1 ? entry.name().substring(dot + 1) : "";
    }

    /**
     * Computes a directory's recursive size off the UI thread and refreshes its Size cell. Skipped
     * when the setting is off or the size is already known, so re-marking costs nothing.
     */
    public static void calculateDirectorySize(FileTab tab, FileEntry entry) {
        if (!entry.directory() || entry.parentLink()
            || !TurtleCommanderSettings.getInstance().getState().calculateDirectorySize
            || tab.getDirectorySizes().containsKey(entry.path())) {
            return;
        }
        UiTask.background(() -> {
            long size = directorySizeOf(entry.path());
            tab.getDirectorySizes().put(entry.path(), size);
            UiTask.ui(() -> {
                for (int row = 0; row < tab.getTableModel().getRowCount(); row++) {
                    FileEntry rowEntry = tab.getTableModel().getEntryAt(row);
                    if (rowEntry != null && rowEntry.path().equals(entry.path())) {
                        tab.getTableModel().fireTableCellUpdated(row, FileTableModel.COL_SIZE);
                        break;
                    }
                }
                tab.updateStatusBar();
            });
        });
    }

    private static long directorySizeOf(Path path) {
        long[] total = {0};
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    total[0] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // An unreadable subtree contributes nothing rather than aborting the walk.
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (Exception e) {
            // Permission denied on the root, or it vanished — report what was summed so far.
        }
        return total[0];
    }

    // --- Cursor ends ---------------------------------------------------------------------------------

    public static void selectFirstRow(FileTab tab) {
        selectRow(tab, 0);
    }

    public static void selectLastRow(FileTab tab) {
        selectRow(tab, tab.getTable().getRowCount() - 1);
    }

    private static void selectRow(FileTab tab, int row) {
        if (row < 0 || row >= tab.getTable().getRowCount()) {
            return;
        }
        tab.getTable().setRowSelectionInterval(row, row);
        tab.getTable().scrollRectToVisible(tab.getTable().getCellRect(row, 0, true));
    }

    public static void saveCurrentSelection(FileTab tab) {
        tab.setSavedMarkedPaths(new HashSet<>(tab.getMarkedPaths()));
    }

    /** No-op when nothing was saved, so the Restore action can stay cheaply enabled. */
    public static void restoreSavedSelection(FileTab tab) {
        Set<Path> saved = tab.getSavedMarkedPaths();
        if (saved == null) {
            return;
        }
        tab.getMarkedPaths().clear();
        tab.getMarkedPaths().addAll(saved);
        applyMarksForCurrentView(tab);
    }

    // --- Cursor movement ---------------------------------------------------------------------------

    public static void moveSelection(FileTab tab, int offset) {
        switch (tab.getViewMode()) {
            case TABLE -> {
                int next = clamp(tab.getTable().getSelectedRow() + offset, tab.getTable().getRowCount());
                if (next >= 0) {
                    tab.getTable().setRowSelectionInterval(next, next);
                    tab.getTable().scrollRectToVisible(tab.getTable().getCellRect(next, 0, true));
                }
            }
            case LIST -> moveListSelection(tab.getList(), tab.getListModel().size(), offset);
            case THUMBNAIL -> moveListSelection(tab.getThumbnailList(), tab.getThumbnailListModel().size(), offset);
            case TREE -> {
                int next = clamp(tab.getTree().getLeadSelectionRow() + offset, tab.getTree().getRowCount());
                if (next >= 0) {
                    tab.getTree().setSelectionRow(next);
                    tab.getTree().scrollRowToVisible(next);
                }
            }
        }
    }

    /** Re-selects {@code name} in every view after a re-list, falling back to the first row. */
    public static void reselectByName(FileTab tab, @Nullable String name) {
        int tableTarget = SelectionTargetUtil.resolveTargetIndex(tab.getTable().getRowCount(),
            i -> {
                FileEntry entry = tab.getTableModel().getEntryAt(tab.getTable().convertRowIndexToModel(i));
                return entry != null ? entry.name() : null;
            }, name, null);
        if (tableTarget != SelectionTargetUtil.NONE) {
            tab.getTable().setRowSelectionInterval(tableTarget, tableTarget);
        }

        selectInList(tab.getList(), tab.getListModel(), name);
        selectInList(tab.getThumbnailList(), tab.getThumbnailListModel(), name);
    }

    private static void selectInList(JBList<FileEntry> list, DefaultListModel<FileEntry> model,
                                     @Nullable String name) {
        int target = SelectionTargetUtil.resolveTargetIndex(model.size(),
            i -> model.getElementAt(i).name(), name, null);
        if (target != SelectionTargetUtil.NONE) {
            list.setSelectedIndex(target);
            list.ensureIndexIsVisible(target);
        }
    }

    private static void moveListSelection(JBList<FileEntry> list, int size, int offset) {
        int next = clamp(list.getSelectedIndex() + offset, size);
        if (next >= 0) {
            list.setSelectedIndex(next);
            list.ensureIndexIsVisible(next);
        }
    }

    private static void moveTableLead(FileTab tab, int row) {
        if (tab.getTable().getSelectionModel() instanceof DefaultListSelectionModel tsm) {
            tsm.moveLeadSelectionIndex(row);
        }
    }

    private static int clamp(int value, int count) {
        if (count <= 0) {
            return -1;
        }
        return Math.max(0, Math.min(value, count - 1));
    }

    private static @Nullable FileEntry leadOf(JBList<FileEntry> list, DefaultListModel<FileEntry> model) {
        int lead = list.getSelectionModel().getLeadSelectionIndex();
        return lead >= 0 && lead < model.size() ? model.getElementAt(lead) : list.getSelectedValue();
    }

    private static @Nullable FileEntry entryOf(@Nullable TreePath path) {
        if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode node)) {
            return null;
        }
        return node.getUserObject() instanceof FileEntry entry ? entry : null;
    }

    private static void addNonParent(List<FileEntry> target, List<FileEntry> source) {
        for (FileEntry entry : source) {
            if (!entry.parentLink()) {
                target.add(entry);
            }
        }
    }
}
