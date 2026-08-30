package consulo.turtlecommander.ui;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.util.UiTask;
import consulo.turtlecommander.vfs.VirtualFileSystem;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.speedSearch.SpeedSearchBase;
import consulo.ui.ex.awt.table.JBTable;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import org.jspecify.annotations.Nullable;

import javax.swing.DefaultListModel;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pushing a listing into the four view models, and switching between them. */
public final class FileTabViewUtil {
    private static final String LOADING_PLACEHOLDER = "Loading...";

    private FileTabViewUtil() {
    }

    /**
     * Replaces the contents of every view model at once.
     *
     * <p>The list models use {@code addAll} rather than a loop of {@code addElement}: the latter
     * fires one {@code ListDataEvent} per entry and becomes a real bottleneck on large directories.
     */
    public static void setEntriesInAllViews(FileTab tab, List<FileEntry> entries) {
        tab.getTableModel().setEntries(entries);

        tab.getListModel().clear();
        tab.getListModel().addAll(entries);

        tab.getThumbnailListModel().clear();
        tab.getThumbnailListModel().addAll(entries);

        // The full-hierarchy tree is the one view a flat listing can't drive, so it is left to
        // rebuildFullTree; every other case renders the same flat entries as the other views.
        if (tab.getViewMode() != ViewMode.TREE || tab.isShowAllNestedFiles()) {
            populateFlatTree(tab);
        }
    }

    /**
     * Rebuilds the tree as a flat list of the filtered entries — used outside full-tree mode.
     * Reading the filtered list (equal to all entries when no filter is active) keeps the flat tree
     * consistent with the other views under an active quick filter.
     */
    public static void populateFlatTree(FileTab tab) {
        DefaultMutableTreeNode root = tab.getTreeRootNode();
        root.removeAllChildren();

        for (FileEntry entry : tab.getFilteredEntries()) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry);
            if (entry.directory() && !entry.parentLink()) {
                // A placeholder child makes the node expandable; the real children load on expand.
                node.add(new DefaultMutableTreeNode(LOADING_PLACEHOLDER));
            }
            root.add(node);
        }
        tab.getTreeModel().nodeStructureChanged(root);
    }

    /** True when the node still holds only the lazy-loading placeholder. */
    public static boolean hasOnlyPlaceholder(DefaultMutableTreeNode node) {
        if (node.getChildCount() != 1) {
            return false;
        }
        return node.getChildAt(0) instanceof DefaultMutableTreeNode child
            && child.getUserObject() instanceof String;
    }

    /** Replaces a node's placeholder with its real children, each expandable if it is a directory. */
    public static void fillChildren(FileTab tab, DefaultMutableTreeNode node, List<FileEntry> children) {
        node.removeAllChildren();
        for (FileEntry child : children) {
            if (child.parentLink()) {
                continue;
            }
            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);
            if (child.directory()) {
                childNode.add(new DefaultMutableTreeNode(LOADING_PLACEHOLDER));
            }
            node.add(childNode);
        }
        tab.getTreeModel().nodeStructureChanged(node);
    }

    /** The card-layout key backing a view mode. */
    public static String cardFor(ViewMode mode) {
        return switch (mode) {
            case TABLE -> FileTab.VIEW_TABLE;
            case LIST -> FileTab.VIEW_LIST;
            case THUMBNAIL -> FileTab.VIEW_THUMBNAIL;
            case TREE -> FileTab.VIEW_TREE;
        };
    }

    /**
     * Switches the visible view, carrying the selection — and any active speed-search prefix —
     * across. Leaving the hierarchical tree also moves the tab to the directory holding the
     * selection, since the flat views can only show one directory at a time.
     */
    public static void setViewMode(FileTab tab, ViewMode mode) {
        ViewMode previousMode = tab.getViewMode();
        if (previousMode == mode) {
            return;
        }

        SpeedSearchBase<?> previousSearch = tab.getSpeedSearches().get(previousMode);
        String carriedPrefix = previousSearch != null ? previousSearch.getEnteredPrefix() : null;
        if (carriedPrefix != null && carriedPrefix.isEmpty()) {
            carriedPrefix = null;
        }

        List<FileEntry> selectedEntries = FileTabSelectionUtil.getSelectedEntries(tab);
        Set<String> selectedNames = new LinkedHashSet<>();
        for (FileEntry entry : selectedEntries) {
            selectedNames.add(entry.name());
        }

        // Tree → flat: land on the directory holding the selection. Skipped inside an archive,
        // where the tree can still show real-filesystem entries whose paths would corrupt the
        // archive state if navigated to.
        TreeNavigation treeNavigation = previousMode == ViewMode.TREE && !selectedEntries.isEmpty()
            && !tab.isInsideArchive()
            ? treeNavigationFor(tab, selectedEntries)
            : null;

        tab.setViewModeField(mode);
        tab.getViewCardLayout().show(tab.getViewPanel(), cardFor(mode));

        if (mode == ViewMode.TABLE && !tab.hasTabColumnState(tab.getCurrentPath().toString())) {
            FileTabColumnUtil.applyDefaultColumnWidths(tab.getTable());
        }

        if (mode == ViewMode.TREE) {
            enterTreeView(tab, selectedNames);
        }
        else {
            enterFlatView(tab, mode, selectedNames, treeNavigation);
        }

        if (carriedPrefix != null) {
            // Deferred so the focus and card transition finish before the popup reattaches.
            String prefix = carriedPrefix;
            SwingUtilities.invokeLater(() -> {
                SpeedSearchBase<?> target = tab.getSpeedSearches().get(mode);
                if (target != null) {
                    target.showPopup(prefix);
                }
            });
        }
    }

    /** Where a tree → flat switch should land, and which of the selected names live there. */
    private record TreeNavigation(Path directory, Set<String> names) {
    }

    private static TreeNavigation treeNavigationFor(FileTab tab, List<FileEntry> selectedEntries) {
        FileEntry last = selectedEntries.get(selectedEntries.size() - 1);
        Path targetDir = parentOr(last.path(), tab.getCurrentPath());

        Set<String> namesInDir = new LinkedHashSet<>();
        for (FileEntry entry : selectedEntries) {
            if (parentOr(entry.path(), tab.getCurrentPath()).equals(targetDir)) {
                namesInDir.add(entry.name());
            }
        }
        return new TreeNavigation(targetDir, namesInDir);
    }

    private static Path parentOr(Path path, Path fallback) {
        Path parent = path.getParent();
        return parent != null ? parent : fallback;
    }

    private static void enterTreeView(FileTab tab, Set<String> selectedNames) {
        // The hierarchical tree can't be name-filtered: drop an active quick filter on the way in
        // rather than leaving the other views' models filtered behind an unfiltered tree.
        if (!tab.isShowAllNestedFiles()) {
            tab.hideQuickFilterIfActive();
            rebuildFullTree(tab, selectedNames);
        }
        else {
            // Show-all-nested-files renders the flat recursive file list as leaves, matching the
            // other views instead of building a hierarchy.
            populateFlatTree(tab);
        }
        tab.getTree().requestFocusInWindow();
    }

    private static void enterFlatView(FileTab tab, ViewMode mode, Set<String> selectedNames,
                                      @Nullable TreeNavigation treeNavigation) {
        if (treeNavigation == null) {
            selectEntriesByNameThenApplyMarks(tab, selectedNames);
        }
        else if (treeNavigation.directory().equals(tab.getCurrentPath())) {
            selectEntriesByNameThenApplyMarks(tab, treeNavigation.names());
        }
        else {
            Set<String> names = treeNavigation.names();
            String first = names.isEmpty() ? null : names.iterator().next();
            UiTask.background(() -> {
                FileTabNavigationUtil.navigateTo(tab, treeNavigation.directory(), first, true);
                UiTask.ui(() -> {
                    if (names.size() > 1) {
                        selectEntriesByNameThenApplyMarks(tab, names);
                    }
                    else if (!tab.getMarkedPaths().isEmpty()) {
                        FileTabSelectionUtil.applyMarksForCurrentView(tab);
                    }
                });
            });
        }

        switch (mode) {
            case THUMBNAIL -> tab.getThumbnailList().requestFocusInWindow();
            case LIST -> tab.getList().requestFocusInWindow();
            default -> tab.getTable().requestFocusInWindow();
        }
    }

    /**
     * Suppresses the per-event mark restore while the selection is rebuilt: the {@code clearSelection}
     * plus N {@code addRowSelectionInterval} calls would each trigger a full O(rowCount) pass. One
     * explicit restore afterwards does the work once.
     */
    private static void selectEntriesByNameThenApplyMarks(FileTab tab, Set<String> names) {
        tab.setInsideViewSwitch(true);
        try {
            selectEntriesByName(tab, names);
        }
        finally {
            tab.setInsideViewSwitch(false);
        }
        if (!tab.getMarkedPaths().isEmpty()) {
            FileTabSelectionUtil.applyMarksForCurrentView(tab);
        }
    }

    public static void selectEntriesByName(FileTab tab, Set<String> names) {
        switch (tab.getViewMode()) {
            case TABLE -> selectTableRowsByName(tab, names);
            case LIST -> selectListIndicesByName(tab.getList(), tab.getListModel(), names);
            case THUMBNAIL -> selectListIndicesByName(tab.getThumbnailList(), tab.getThumbnailListModel(), names);
            case TREE -> {
                // The tree's selection is rebuilt wholesale by rebuildFullTree.
            }
        }
    }

    private static void selectTableRowsByName(FileTab tab, Set<String> names) {
        JBTable table = tab.getTable();
        table.clearSelection();
        int firstSelectedRow = -1;
        for (int viewRow = 0; viewRow < table.getRowCount(); viewRow++) {
            FileEntry entry = tab.getTableModel().getEntryAt(table.convertRowIndexToModel(viewRow));
            if (entry != null && names.contains(entry.name())) {
                table.addRowSelectionInterval(viewRow, viewRow);
                if (firstSelectedRow == -1) {
                    firstSelectedRow = viewRow;
                }
            }
        }
        if (firstSelectedRow >= 0) {
            table.scrollRectToVisible(table.getCellRect(firstSelectedRow, 0, true));
        }
    }

    private static void selectListIndicesByName(JBList<FileEntry> list, DefaultListModel<FileEntry> model,
                                                Set<String> names) {
        list.clearSelection();
        IntList indices = IntLists.newArrayList();
        for (int i = 0; i < model.size(); i++) {
            if (names.contains(model.getElementAt(i).name())) {
                indices.add(i);
            }
        }
        if (indices.isEmpty()) {
            return;
        }
        list.setSelectedIndices(indices.toArray());
        list.ensureIndexIsVisible(indices.get(0));
    }

    /**
     * Rebuilds the tree as the real directory hierarchy from the root down to the current path,
     * with every level's siblings listed and the ancestor chain expanded.
     *
     * <p>Listing happens on a background thread; the node rebuild is pushed back onto the UI thread.
     */
    public static void rebuildFullTree(FileTab tab) {
        rebuildFullTree(tab, Set.of());
    }

    public static void rebuildFullTree(FileTab tab, Set<String> selectNames) {
        UiTask.background(() -> {
            VirtualFileSystem vfs = tab.getCurrentVfs();
            List<Path> ancestors = ancestorChain(tab, vfs);
            List<List<FileEntry>> levels = new ArrayList<>(ancestors.size());
            for (Path ancestor : ancestors) {
                levels.add(listQuietly(tab, vfs, ancestor));
            }
            UiTask.ui(() -> applyFullTree(tab, ancestors, levels, selectNames));
        });
    }

    /** Root first, current path last. */
    private static List<Path> ancestorChain(FileTab tab, @Nullable VirtualFileSystem vfs) {
        List<Path> ancestors = new ArrayList<>();
        if (vfs != null) {
            for (Path p = tab.getCurrentPath(); p != null && !vfs.isRoot(p); p = p.getParent()) {
                ancestors.add(0, p);
            }
            ancestors.add(0, vfs.getRoot());
        }
        else {
            for (Path p = tab.getCurrentPath(); p != null; p = p.getParent()) {
                ancestors.add(0, p);
            }
        }
        return ancestors;
    }

    /** An unreadable level renders as empty rather than aborting the whole rebuild. */
    private static List<FileEntry> listQuietly(FileTab tab, @Nullable VirtualFileSystem vfs, Path path) {
        List<FileEntry> entries;
        try {
            entries = vfs != null ? vfs.listFiles(path) : tab.getFileOps().listFiles(path);
        }
        catch (Exception e) {
            return List.of();
        }
        List<FileEntry> result = new ArrayList<>(entries.size());
        for (FileEntry entry : entries) {
            if (!entry.parentLink()) {
                result.add(entry);
            }
        }
        return result;
    }

    private static void applyFullTree(FileTab tab, List<Path> ancestors, List<List<FileEntry>> levels,
                                      Set<String> selectNames) {
        DefaultMutableTreeNode root = tab.getTreeRootNode();
        List<DefaultMutableTreeNode> ancestorNodes = new ArrayList<>();

        tab.setInsideViewSwitch(true);
        try {
            root.removeAllChildren();
            DefaultMutableTreeNode currentParent = root;

            for (int levelIdx = 0; levelIdx < levels.size(); levelIdx++) {
                Path nextAncestor = levelIdx + 1 < ancestors.size() ? ancestors.get(levelIdx + 1) : null;
                DefaultMutableTreeNode nextParent = null;

                for (FileEntry entry : levels.get(levelIdx)) {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry);
                    if (entry.directory() && nextParent == null && nextAncestor != null
                        && samePath(entry.path(), nextAncestor)) {
                        // This directory is on the ancestor chain — its children come from the
                        // next level rather than from a lazy placeholder.
                        nextParent = node;
                        ancestorNodes.add(node);
                    }
                    else if (entry.directory()) {
                        node.add(new DefaultMutableTreeNode(LOADING_PLACEHOLDER));
                    }
                    currentParent.add(node);
                }

                // The ancestor may be missing from its parent's listing (hidden, or a permission
                // error) — synthesise a node so the chain down to the current path stays intact.
                if (nextParent == null && nextAncestor != null) {
                    Path fileName = nextAncestor.getFileName();
                    FileEntry synthetic = FileEntry
                        .builder(fileName != null ? fileName.toString() : nextAncestor.toString(), nextAncestor)
                        .directory(true)
                        .build();
                    nextParent = new DefaultMutableTreeNode(synthetic);
                    ancestorNodes.add(nextParent);
                    currentParent.add(nextParent);
                }

                if (nextParent != null) {
                    currentParent = nextParent;
                }
            }

            tab.getTreeModel().nodeStructureChanged(root);
            for (DefaultMutableTreeNode node : ancestorNodes) {
                tab.getTree().expandPath(new TreePath(node.getPath()));
            }
            selectTreeNodesByName(tab, ancestorNodes, selectNames);
        }
        finally {
            tab.setInsideViewSwitch(false);
        }

        if (!tab.getMarkedPaths().isEmpty()) {
            FileTabSelectionUtil.applyMarksToTree(tab);
        }
    }

    private static void selectTreeNodesByName(FileTab tab, List<DefaultMutableTreeNode> ancestorNodes,
                                              Set<String> selectNames) {
        if (selectNames.isEmpty()) {
            return;
        }
        DefaultMutableTreeNode currentDirNode = ancestorNodes.isEmpty()
            ? tab.getTreeRootNode()
            : ancestorNodes.get(ancestorNodes.size() - 1);

        List<TreePath> paths = new ArrayList<>();
        for (int i = 0; i < currentDirNode.getChildCount(); i++) {
            if (currentDirNode.getChildAt(i) instanceof DefaultMutableTreeNode child
                && child.getUserObject() instanceof FileEntry entry
                && selectNames.contains(entry.name())) {
                paths.add(new TreePath(child.getPath()));
            }
        }
        if (paths.isEmpty()) {
            return;
        }
        tab.getTree().setSelectionPaths(paths.toArray(new TreePath[0]));
        tab.getTree().scrollPathToVisible(paths.get(0));
    }

    /** Tolerates the relative/absolute and un-normalised forms a listing can hand back. */
    private static boolean samePath(Path a, Path b) {
        return a.equals(b)
            || a.normalize().equals(b.normalize())
            || a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }
}
