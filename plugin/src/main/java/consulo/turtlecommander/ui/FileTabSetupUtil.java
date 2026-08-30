package consulo.turtlecommander.ui;

import consulo.application.dumb.DumbAware;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.settings.ThumbnailSize;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.util.UiTask;
import consulo.turtlecommander.vfs.VirtualFileSystem;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.ex.action.KeyboardShortcut;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.JBScrollPane;
import consulo.ui.ex.awt.speedSearch.ListSpeedSearch;
import consulo.ui.ex.awt.speedSearch.SpeedSearchSupply;
import consulo.ui.ex.awt.speedSearch.TableSpeedSearch;
import consulo.ui.ex.awt.speedSearch.TreeSpeedSearch;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.tree.Tree;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds the four views and the header/footer chrome of a {@link FileTab}, and installs the
 * listeners and component-local shortcuts they need.
 *
 * <p>Component-local shortcuts (Space, Insert, Home/End, Ctrl+S) are registered as platform actions
 * bound to a single component rather than as Swing key bindings: that way they take priority over
 * both the IDE's global bindings and the look and feel's own defaults.
 */
public final class FileTabSetupUtil {
    private FileTabSetupUtil() {
    }

    public static void setUp(FileTab tab) {
        setupHeader(tab);
        setupTable(tab);
        setupList(tab);
        setupThumbnailList(tab);
        setupTree(tab);
        registerQuickFilterShortcut(tab);
        setupViewPanel(tab);
    }

    // --- Header --------------------------------------------------------------------------------------

    private static void setupHeader(FileTab tab) {
        DriveSelectionUtil.install(tab);

        BreadcrumbPathField pathField = tab.getPathField();
        pathField.setOnSegmentClick(segmentPath -> {
            if (tab.isInsideArchive()) {
                FileTabVfsUtil.handleVfsBreadcrumbClick(tab, segmentPath);
                return;
            }
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
        });

        pathField.addActionListener(e -> {
            if (tab.getCurrentVfs() != null) {
                // Inside an archive the path bar is display-only.
                tab.focusActiveView();
                return;
            }
            Path typed = parseTypedPath(pathField.getText(), tab.getCurrentPath());
            Path fallback = tab.getCurrentPath();
            // The ancestor walk stats each level; on a dead UNC path every stat can block for the
            // SMB timeout, so the whole resolution runs off the UI thread.
            UiTask.background(() -> {
                Path path = typed;
                while (path != null && !Files.isDirectory(path)) {
                    path = path.getParent();
                }
                Path resolved = path != null ? path : fallback;
                UiTask.ui(() -> pathField.setText(resolved.toString()));
                FileTabNavigationUtil.navigateTo(tab, resolved);
            });
            tab.focusActiveView();
        });

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(tab.getDriveCombo().getComponent(), BorderLayout.WEST);
        headerPanel.add(pathField, BorderLayout.CENTER);
        tab.add(headerPanel, BorderLayout.NORTH);
    }

    /** A relative entry is meaningless as a jump target, so it falls back to the current directory. */
    private static Path parseTypedPath(String text, Path currentPath) {
        try {
            Path parsed = Path.of(text);
            return parsed.isAbsolute() ? parsed : currentPath;
        }
        catch (Exception e) {
            return currentPath;
        }
    }

    // --- Table ---------------------------------------------------------------------------------------

    private static void setupTable(FileTab tab) {
        JBTable table = tab.getTable();
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setCellSelectionEnabled(false);
        table.setRowSelectionAllowed(true);
        table.setAutoResizeMode(JBTable.AUTO_RESIZE_OFF);
        table.setRowHeight(20);

        FileTabColumnUtil.applyColumnConfig(tab);
        FileTabHeaderMenuUtil.install(tab);
        table.setRowSorter(new ParentPinningRowSorter(tab.getTableModel()));
        tab.installNameEditor();

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // A double-click opens; cancel any rename armed by its first click.
                    tab.cancelPendingRename();
                    FileTabNavigationUtil.openSelectedEntry(tab);
                    return;
                }
                if (e.getClickCount() == 1) {
                    if (MouseGestureUtil.maybeSingleClickOpenDirectory(tab, e,
                        table.rowAtPoint(e.getPoint()) >= 0)) {
                        return;
                    }
                    tab.maybeArmOrStartRename(e);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // Any new gesture cancels a pending click-to-rename.
                tab.cancelPendingRename();
                FileTabContextMenuUtil.handleTableContextMenu(tab, e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                FileTabContextMenuUtil.handleTableContextMenu(tab, e);
            }
        });

        // The cursor row is painted differently depending on focus, so a focus change has to
        // repaint the whole table rather than just the focus owner.
        table.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                table.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                table.repaint();
            }
        });

        table.setDragEnabled(true);
        table.setTransferHandler(new FileEntryTransferHandler(tab));

        table.getSelectionModel().addListSelectionListener(e -> {
            if (shouldRestoreMarks(tab)) {
                FileTabSelectionUtil.applyMarksToTable(tab);
            }
            // A selection change means this click (re)selected a row rather than landing on an
            // already-selected one, so disarm click-to-rename.
            tab.disarmRename();
            tab.updateStatusBar();
        });
    }

    // --- List and thumbnails -------------------------------------------------------------------------

    private static void setupList(FileTab tab) {
        JBList<FileEntry> list = tab.getList();
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setLayoutOrientation(JList.VERTICAL_WRAP);
        list.setVisibleRowCount(0);
        list.setCellRenderer(new FileListCellRenderer(tab));

        installListMouseHandling(tab, list, FileTabContextMenuUtil::handleListContextMenu);
        list.getSelectionModel().addListSelectionListener(e -> {
            if (shouldRestoreMarks(tab)) {
                FileTabSelectionUtil.applyMarksToList(tab, list, tab.getListModel());
            }
            tab.updateStatusBar();
            ensureSpeedSearchSelectionVisible(list);
        });

        ListKeyBindingUtil.installHomeEndBindings(list);
        installToggleSelectionBinding(tab, list);
    }

    private static void setupThumbnailList(FileTab tab) {
        JBList<FileEntry> list = tab.getThumbnailList();
        ThumbnailSize initialSize =
            ThumbnailSize.fromName(TurtleCommanderSettings.getInstance().getState().thumbnailSize);

        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(0);
        list.setFixedCellWidth(initialSize.getCellWidth());
        list.setFixedCellHeight(initialSize.getCellHeight());
        list.setCellRenderer(new FileThumbnailCellRenderer(tab));

        installListMouseHandling(tab, list, FileTabContextMenuUtil::handleThumbnailContextMenu);
        list.getSelectionModel().addListSelectionListener(e -> {
            if (shouldRestoreMarks(tab)) {
                FileTabSelectionUtil.applyMarksToList(tab, list, tab.getThumbnailListModel());
            }
            tab.updateStatusBar();
            ensureSpeedSearchSelectionVisible(list);
        });

        ListKeyBindingUtil.installHomeEndBindings(list);
        installToggleSelectionBinding(tab, list);
    }

    private static void installListMouseHandling(FileTab tab, JBList<FileEntry> list,
                                                 ContextMenuHandler contextMenu) {
        list.setDragEnabled(true);
        list.setTransferHandler(new FileEntryTransferHandler(tab));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    FileTabNavigationUtil.openSelectedEntry(tab);
                    return;
                }
                if (e.getClickCount() == 1) {
                    int index = list.locationToIndex(e.getPoint());
                    Rectangle bounds = index >= 0 ? list.getCellBounds(index, index) : null;
                    boolean onItem = bounds != null && bounds.contains(e.getPoint());
                    MouseGestureUtil.maybeSingleClickOpenDirectory(tab, e, onItem);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                contextMenu.handle(tab, e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                contextMenu.handle(tab, e);
            }
        });
    }

    @FunctionalInterface
    private interface ContextMenuHandler {
        void handle(FileTab tab, MouseEvent e);
    }

    /**
     * A wrapping {@link JList} doesn't auto-scroll on programmatic selection changes, which is how
     * speed-search Up/Down moves the cursor — so force it here.
     */
    private static void ensureSpeedSearchSelectionVisible(JBList<FileEntry> list) {
        if (SpeedSearchSupply.getSupply(list) == null) {
            return;
        }
        int index = list.getSelectedIndex();
        if (index >= 0) {
            list.ensureIndexIsVisible(index);
        }
    }

    // --- Tree ----------------------------------------------------------------------------------------

    private static void setupTree(FileTab tab) {
        Tree tree = tab.getTree();
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setBackground(tab.getTable().getBackground());
        tree.setDragEnabled(true);
        tree.setTransferHandler(new FileEntryTransferHandler(tab));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        tree.setCellRenderer(new FileTreeCellRenderer(tab));

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                if (tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof FileEntry entry) {
                    handleTreeDoubleClick(tab, entry);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                FileTabContextMenuUtil.handleTreeContextMenu(tab, e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                FileTabContextMenuUtil.handleTreeContextMenu(tab, e);
            }
        });

        tree.addTreeSelectionListener(e -> {
            if (shouldRestoreMarks(tab)) {
                FileTabSelectionUtil.applyMarksToTree(tab);
            }
            tab.updateStatusBar();
        });

        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                expandLazily(tab, event);
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
            }
        });

        installToggleSelectionBinding(tab, tree);
    }

    /** Directories expand rather than open; everything else follows the normal open path. */
    private static void handleTreeDoubleClick(FileTab tab, FileEntry entry) {
        if (entry.parentLink()) {
            FileTabNavigationUtil.goUp(tab);
        }
        else if (entry.directory()) {
            // Handled by the tree's own expansion.
        }
        else if (tab.isEntryBrowsableArchive(entry)) {
            FileTabVfsUtil.enterVfs(tab, entry);
        }
        else {
            FileTabOperationUtil.openFile(tab, entry);
        }
    }

    private static void expandLazily(FileTab tab, TreeExpansionEvent event) {
        if (!(event.getPath().getLastPathComponent() instanceof DefaultMutableTreeNode node)
            || !(node.getUserObject() instanceof FileEntry entry)) {
            return;
        }
        if (!entry.directory() || entry.parentLink() || !FileTabViewUtil.hasOnlyPlaceholder(node)) {
            return;
        }
        UiTask.background(() -> {
            List<FileEntry> children;
            try {
                VirtualFileSystem vfs = tab.getCurrentVfs();
                children = vfs != null ? vfs.listFiles(entry.path()) : tab.getFileOps().listFiles(entry.path());
            }
            catch (Exception e) {
                // An unreadable directory just stays collapsed.
                return;
            }
            UiTask.ui(() -> FileTabViewUtil.fillChildren(tab, node, children));
        });
    }

    // --- Shortcuts and the view stack -------------------------------------------------------------------

    private static void installToggleSelectionBinding(FileTab tab, JComponent component) {
        ListKeyBindingUtil.installToggleSelectionBinding(component,
            () -> FileTabSelectionUtil.toggleSelection(tab),
            () -> FileTabSelectionUtil.toggleSelectionAndMoveDown(tab));
    }

    private static void registerQuickFilterShortcut(FileTab tab) {
        // Ctrl+S is the IDE's global "Save All"; a component-local action outranks it.
        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);
        for (JComponent component : List.of(tab.getTable(), tab.getList(), tab.getThumbnailList(),
            tab.getTree())) {
            ListKeyBindingUtil.installShortcut(component, stroke, tab::showQuickFilter);
        }
    }

    private static void setupViewPanel(FileTab tab) {
        JPanel viewPanel = tab.getViewPanel();
        viewPanel.add(new JBScrollPane(tab.getTable()), FileTab.VIEW_TABLE);
        viewPanel.add(new JBScrollPane(tab.getList()), FileTab.VIEW_LIST);
        viewPanel.add(new JBScrollPane(tab.getThumbnailList()), FileTab.VIEW_THUMBNAIL);
        viewPanel.add(new JBScrollPane(tab.getTree()), FileTab.VIEW_TREE);
        installSpeedSearch(tab);

        tab.getViewCardLayout().show(viewPanel, FileTabViewUtil.cardFor(tab.getViewMode()));
        tab.add(viewPanel, BorderLayout.CENTER);

        tab.getStatusLabel().setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        tab.getFreeSpaceLabel().setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        tab.getFreeSpaceLabel().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tab.getFreeSpaceLabel().setToolTipText("Click to view drive space");
        tab.getFreeSpaceLabel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                tab.showDriveSpace();
            }
        });
        tab.getStatusPanel().add(tab.getStatusLabel(), BorderLayout.WEST);
        tab.getStatusPanel().add(tab.getFreeSpaceLabel(), BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(tab.getQuickFilterBar(), BorderLayout.NORTH);
        bottomPanel.add(tab.getStatusPanel(), BorderLayout.SOUTH);
        tab.add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Typing in a focused view opens the platform's search popup, highlights matched substrings and
     * lets Up/Down jump between matches. Each converter returns only the entry's display name, so
     * columns the user can't see (size, date) don't pollute the matches.
     */
    private static void installSpeedSearch(FileTab tab) {
        tab.getSpeedSearches().put(ViewMode.TABLE,
            new TableSpeedSearch(tab.getTable(), value -> value instanceof FileEntry entry
                ? entry.name()
                : value != null ? value.toString() : ""));
        tab.getSpeedSearches().put(ViewMode.LIST,
            ListSpeedSearch.installOn(tab.getList(), FileEntry::name));
        tab.getSpeedSearches().put(ViewMode.THUMBNAIL,
            ListSpeedSearch.installOn(tab.getThumbnailList(), FileEntry::name));
        tab.getSpeedSearches().put(ViewMode.TREE,
            TreeSpeedSearch.installOn(tab.getTree(), true, path -> {
                if (path.getLastPathComponent() instanceof DefaultMutableTreeNode node) {
                    return node.getUserObject() instanceof FileEntry entry
                        ? entry.name()
                        : String.valueOf(node.getUserObject());
                }
                return "";
            }));
    }

    /**
     * Marks are restored from the selection listener, but only when the change came from the user.
     * The toggle, restore and view-switch flags mark changes we made ourselves — restoring during
     * those would either recurse or turn one view switch into O(N × rowCount) work.
     */
    private static boolean shouldRestoreMarks(FileTab tab) {
        return !tab.isInsideToggle() && !tab.isInsideRestore() && !tab.isInsideViewSwitch()
            && !tab.getMarkedPaths().isEmpty();
    }
}
