package consulo.turtlecommander.ui;

import consulo.disposer.Disposable;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.dialog.DriveSpaceDialog;
import consulo.turtlecommander.filetype.TurtleCommanderNotifications;
import consulo.turtlecommander.model.DriveInfo;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.service.FileOperationService;
import consulo.turtlecommander.service.TabState;
import consulo.turtlecommander.settings.ComponentStyle;
import consulo.turtlecommander.settings.ThumbnailSize;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.util.UiTask;
import consulo.turtlecommander.vfs.VfsStackEntry;
import consulo.turtlecommander.vfs.VirtualFileSystem;
import consulo.turtlecommander.vfs.VirtualFileSystemRegistry;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.speedSearch.SpeedSearchBase;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.tree.Tree;
import org.jspecify.annotations.Nullable;

import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.Timer;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One tab in a panel: the four views over a directory, the path bar, drive selector, quick filter
 * and status bar.
 *
 * <p>The behaviour is split across sibling {@code FileTab*Util} classes that take this tab as their
 * first argument — view construction, selection and marks, VFS entry/exit, drag and drop, context
 * menus, and the file operations. This class owns the state they operate on and exposes it through
 * accessors.
 */
public class FileTab extends JPanel implements Disposable {
    private static final Logger LOG = Logger.getInstance(FileTab.class);

    private static final int DEFAULT_ROW_HEIGHT = 20;

    static final String VIEW_TABLE = "table";
    static final String VIEW_LIST = "list";
    static final String VIEW_THUMBNAIL = "thumbnail";
    static final String VIEW_TREE = "tree";

    private final Project myProject;
    private final FileOperationService myFileOps;

    // --- Views ----------------------------------------------------------------------------------

    private final FileTableModel myTableModel = new FileTableModel();
    private final JBTable myTable = new JBTable(myTableModel);

    private final DefaultListModel<FileEntry> myListModel = new DefaultListModel<>();
    private final JBList<FileEntry> myList = new JBList<>(myListModel);

    private final DefaultListModel<FileEntry> myThumbnailListModel = new DefaultListModel<>();
    private final JBList<FileEntry> myThumbnailList = new JBList<>(myThumbnailListModel);

    private final DefaultMutableTreeNode myTreeRootNode = new DefaultMutableTreeNode();
    private final DefaultTreeModel myTreeModel = new DefaultTreeModel(myTreeRootNode);
    private final Tree myTree = new Tree(myTreeModel);

    private final CardLayout myViewCardLayout = new CardLayout();
    private final JPanel myViewPanel = new JPanel(myViewCardLayout);

    /** One speed search per view, so a prefix typed in one can be carried into another. */
    private final Map<ViewMode, SpeedSearchBase<?>> mySpeedSearches = new EnumMap<>(ViewMode.class);

    // --- Header / footer ------------------------------------------------------------------------

    private final DriveComboController myDriveCombo = new DriveComboController(() -> getCurrentVfs() != null);
    private final BreadcrumbPathField myPathField = new BreadcrumbPathField();
    private final JLabel myStatusLabel = new JLabel(" ");
    private final JLabel myFreeSpaceLabel = new JLabel(" ");
    private final JPanel myStatusPanel = new JPanel(new BorderLayout());
    private final QuickFilterBar myQuickFilterBar;
    private final QuickFilterMatcher myFilterMatcher = new QuickFilterMatcher();

    // --- Listing state --------------------------------------------------------------------------

    private Path myCurrentPath;
    private ViewMode myViewMode;
    private boolean myShowAllNestedFiles;

    private List<FileEntry> myAllEntries = List.of();
    private List<FileEntry> myFilteredEntries = List.of();

    private final Map<Path, Integer> myCursorPositions = new HashMap<>();
    private final Map<String, ColumnStateUtil.ColumnState> myTabColumnStates = new HashMap<>();
    private final Map<Path, Long> myDirectorySizes = new ConcurrentHashMap<>();

    private @Nullable Path myFreeSpaceLastPath;
    private boolean myInitialized;

    // Look-and-feel values captured before any styling is applied, so clearing a style setting can
    // restore the real default rather than whatever was set last.
    private final Font myDefaultViewFont = myTable.getFont();
    private final Color myDefaultViewForeground = myTable.getForeground();
    private final Color myDefaultViewBackground = myTable.getBackground();
    private final Color myDefaultSelectionBackground = myTable.getSelectionBackground();
    private final Font myDefaultDriveComboFont = myDriveCombo.getComponent().getFont();
    private final Color myDefaultDriveComboForeground = myDriveCombo.getComponent().getForeground();
    private final Color myDefaultDriveComboBackground = myDriveCombo.getComponent().getBackground();
    private final @Nullable Font myDefaultHeaderFont =
        myTable.getTableHeader() != null ? myTable.getTableHeader().getFont() : null;
    private final @Nullable Color myDefaultHeaderForeground =
        myTable.getTableHeader() != null ? myTable.getTableHeader().getForeground() : null;
    private final @Nullable Color myDefaultHeaderBackground =
        myTable.getTableHeader() != null ? myTable.getTableHeader().getBackground() : null;
    private final Font myDefaultStatusFont = myStatusLabel.getFont();
    private final Color myDefaultStatusForeground = myStatusLabel.getForeground();
    private final Color myDefaultStatusBackground = myStatusPanel.getBackground();

    /** Unsubscribes this tab from the shared drive-roots poller; null when not subscribed. */
    private @Nullable Runnable myUnsubscribeDriveRoots;

    private @Nullable DefaultCellEditor myNameEditor;
    private @Nullable FileEntry myEditingEntry;

    /**
     * Explorer-style "click the name of the already-selected item to rename". A click only
     * <em>arms</em> a row once it is the sole selection; the next single click on that same name
     * starts the rename. The armed view row is -1 when nothing is armed, and is cleared whenever
     * the selection changes. The timer defers the rename by the double-click interval so a
     * double-click — which opens the entry — cancels it instead.
     */
    private int myRenameArmedRow = -1;
    private @Nullable Timer myRenameClickTimer;

    /** Master switch mirrored from settings, so renderers don't re-read it per cell. */
    private boolean myEnableFileNameHighlighting =
        TurtleCommanderSettings.getInstance().getState().enableFileNameHighlighting;

    // --- Marks ----------------------------------------------------------------------------------

    /**
     * Persistent marks (Insert/Space) keyed by absolute path, so they survive sort changes and
     * view-mode switches. Each renderer translates back to view rows on the fly.
     */
    private final Set<Path> myMarkedPaths = new HashSet<>();

    /** Snapshot taken by "Save Selection"; null means nothing has been saved yet. */
    private @Nullable Set<Path> mySavedMarkedPaths;

    private boolean myInsideToggle;

    /**
     * Set while a selection listener is restoring marked rows after Swing's default arrow/click
     * handler clobbered them, so the restore can't trigger itself.
     */
    private boolean myInsideRestore;

    /**
     * Set while a view switch rebuilds the selection. Without it every {@code addRowSelectionInterval}
     * fires the listener and runs a full mark restore, making a view switch O(N * rowCount).
     */
    private boolean myInsideViewSwitch;

    // --- Archive stack --------------------------------------------------------------------------

    private final List<VfsStackEntry> myVfsStack = new ArrayList<>();

    /** Serialises write-back against the shared stack; see {@code VfsEditService}. */
    private final ReentrantLock myVfsWriteLock = new ReentrantLock();

    // --- Panel wiring ---------------------------------------------------------------------------

    private Supplier<@Nullable Path> myOtherPanelPathProvider;
    private Consumer<FileTab> myOnDirectoryChanged;
    private Runnable myOnRefreshOtherPanel;

    private @Nullable FileManagerStateService myStateService;

    /**
     * State restored from disk, applied on the first listing rather than at construction: the
     * column widths it carries only mean anything once the table has rows and a sizing pass.
     */
    private @Nullable TabState myPendingTabState;

    public FileTab(Project project, Path initialPath, Supplier<@Nullable Path> otherPanelPathProvider,
                   Consumer<FileTab> onDirectoryChanged, Runnable onRefreshOtherPanel) {
        super(new BorderLayout());

        myProject = project;
        myFileOps = project.getInstance(FileOperationService.class);
        myCurrentPath = initialPath;
        myOtherPanelPathProvider = otherPanelPathProvider;
        myOnDirectoryChanged = onDirectoryChanged;
        myOnRefreshOtherPanel = onRefreshOtherPanel;

        myViewMode = ViewMode.fromName(TurtleCommanderSettings.getInstance().getState().defaultViewMode);
        myTableModel.setDirectorySizeProvider(myDirectorySizes::get);

        myQuickFilterBar = new QuickFilterBar(
            text -> applyFilter(),
            this::hideQuickFilter,
            this::moveSelection,
            this::focusActiveView);
    }

    /**
     * Builds the views and chrome. Split out of the constructor because the setup registers
     * listeners that call back into the tab, which must not run before it is fully constructed.
     */
    public void initializeViews() {
        FileTabSetupUtil.setUp(this);
        if (!TurtleCommanderSettings.getInstance().getState().hideDriveSelector) {
            startDriveUpdates();
        }
        applyPanelFont();
        applyVisibilitySettings();

        // The tabbed pane transfers focus to the first focusable descendant after a tab switch,
        // which would be the drive combo; point it at the active view instead.
        setFocusCycleRoot(true);
        setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
            @Override
            public Component getDefaultComponent(Container container) {
                return activeViewComponent();
            }
        });
    }

    // --- Accessors used by the FileTab*Util helpers ----------------------------------------------

    public Project getProject() {
        return myProject;
    }

    public FileOperationService getFileOps() {
        return myFileOps;
    }

    public FileTableModel getTableModel() {
        return myTableModel;
    }

    public JBTable getTable() {
        return myTable;
    }

    public DefaultListModel<FileEntry> getListModel() {
        return myListModel;
    }

    public JBList<FileEntry> getList() {
        return myList;
    }

    public DefaultListModel<FileEntry> getThumbnailListModel() {
        return myThumbnailListModel;
    }

    public JBList<FileEntry> getThumbnailList() {
        return myThumbnailList;
    }

    public DefaultMutableTreeNode getTreeRootNode() {
        return myTreeRootNode;
    }

    public DefaultTreeModel getTreeModel() {
        return myTreeModel;
    }

    public Tree getTree() {
        return myTree;
    }

    public JPanel getViewPanel() {
        return myViewPanel;
    }

    public Map<ViewMode, SpeedSearchBase<?>> getSpeedSearches() {
        return mySpeedSearches;
    }

    public CardLayout getViewCardLayout() {
        return myViewCardLayout;
    }

    public DriveComboController getDriveCombo() {
        return myDriveCombo;
    }

    public BreadcrumbPathField getPathField() {
        return myPathField;
    }

    public JLabel getStatusLabel() {
        return myStatusLabel;
    }

    public JLabel getFreeSpaceLabel() {
        return myFreeSpaceLabel;
    }

    public JPanel getStatusPanel() {
        return myStatusPanel;
    }

    public QuickFilterBar getQuickFilterBar() {
        return myQuickFilterBar;
    }

    public boolean isEnableFileNameHighlighting() {
        return myEnableFileNameHighlighting;
    }

    public Path getCurrentPath() {
        return myCurrentPath;
    }

    public void setCurrentPath(Path currentPath) {
        myCurrentPath = currentPath;
    }

    public ViewMode getViewMode() {
        return myViewMode;
    }

    public void setViewModeField(ViewMode viewMode) {
        myViewMode = viewMode;
    }

    public boolean isShowAllNestedFiles() {
        return myShowAllNestedFiles;
    }

    public void setShowAllNestedFilesField(boolean value) {
        myShowAllNestedFiles = value;
    }

    public List<FileEntry> getAllEntries() {
        return myAllEntries;
    }

    public void setAllEntries(List<FileEntry> entries) {
        myAllEntries = entries;
    }

    public List<FileEntry> getFilteredEntries() {
        return myFilteredEntries;
    }

    public void setFilteredEntries(List<FileEntry> entries) {
        myFilteredEntries = entries;
    }

    public Map<Path, Integer> getCursorPositions() {
        return myCursorPositions;
    }

    public Map<String, ColumnStateUtil.ColumnState> getTabColumnStates() {
        return myTabColumnStates;
    }

    public Map<Path, Long> getDirectorySizes() {
        return myDirectorySizes;
    }

    public Set<Path> getMarkedPaths() {
        return myMarkedPaths;
    }

    public @Nullable Set<Path> getSavedMarkedPaths() {
        return mySavedMarkedPaths;
    }

    public void setSavedMarkedPaths(@Nullable Set<Path> paths) {
        mySavedMarkedPaths = paths;
    }

    public boolean isInsideToggle() {
        return myInsideToggle;
    }

    public void setInsideToggle(boolean value) {
        myInsideToggle = value;
    }

    public boolean isInsideRestore() {
        return myInsideRestore;
    }

    public void setInsideRestore(boolean value) {
        myInsideRestore = value;
    }

    public boolean isInsideViewSwitch() {
        return myInsideViewSwitch;
    }

    public void setInsideViewSwitch(boolean value) {
        myInsideViewSwitch = value;
    }

    public List<VfsStackEntry> getVfsStack() {
        return myVfsStack;
    }

    public ReentrantLock getVfsWriteLock() {
        return myVfsWriteLock;
    }

    public boolean isInitialized() {
        return myInitialized;
    }

    public void setInitialized(boolean initialized) {
        myInitialized = initialized;
    }

    public @Nullable Path getOtherPanelPath() {
        return myOtherPanelPathProvider.get();
    }

    public void notifyDirectoryChanged() {
        myOnDirectoryChanged.accept(this);
    }

    public void refreshOtherPanel() {
        myOnRefreshOtherPanel.run();
    }

    public void updatePanelCallbacks(Supplier<@Nullable Path> otherPanelPathProvider,
                                     Consumer<FileTab> onDirectoryChanged, Runnable onRefreshOtherPanel) {
        myOtherPanelPathProvider = otherPanelPathProvider;
        myOnDirectoryChanged = onDirectoryChanged;
        myOnRefreshOtherPanel = onRefreshOtherPanel;
    }

    public void setStateService(FileManagerStateService stateService) {
        myStateService = stateService;
    }

    /** The active tab of the opposite panel, or null when the layout isn't wired up yet. */
    public @Nullable FileTab getOtherPanelTab() {
        if (myStateService == null) {
            return null;
        }
        FileManagerPanel activePanel = myStateService.getActivePanel();
        if (activePanel == null) {
            return null;
        }
        FileManagerPanel otherPanel = activePanel == myStateService.getLeftPanel()
            ? myStateService.getRightPanel()
            : myStateService.getLeftPanel();
        return otherPanel != null ? otherPanel.getActiveTab() : null;
    }

    public @Nullable String getOtherPanelDisplayPath() {
        FileTab other = getOtherPanelTab();
        return other != null ? FileTabVfsUtil.getDisplayPath(other) : null;
    }

    public void setPendingTabState(TabState state) {
        myPendingTabState = state;
    }

    /** Consumes the restored state — it must only be applied to the first listing. */
    public @Nullable TabState takePendingTabState() {
        TabState state = myPendingTabState;
        myPendingTabState = null;
        return state;
    }

    public void fileErrorNotification(String content) {
        TurtleCommanderNotifications.error(myProject, content);
    }

    // --- Archive state ---------------------------------------------------------------------------

    public @Nullable VirtualFileSystem getCurrentVfs() {
        return myVfsStack.isEmpty() ? null : myVfsStack.get(myVfsStack.size() - 1).getVfs();
    }

    public boolean isInsideArchive() {
        return !myVfsStack.isEmpty();
    }

    /** The real on-disk path this tab maps to: the outermost archive when inside one. */
    public Path getRealFilesystemPath() {
        return myVfsStack.isEmpty() ? myCurrentPath : myVfsStack.get(0).getParentPath();
    }

    public boolean isEntryBrowsableArchive(FileEntry entry) {
        if (entry.directory() || entry.parentLink()) {
            return false;
        }
        // Inside an archive the entry is a temp-dir copy, so only its name is meaningful; on the
        // real filesystem the provider can sniff the file itself.
        return getCurrentVfs() != null
            ? VirtualFileSystemRegistry.supportsByExtension(entry.name())
            : VirtualFileSystemRegistry.supports(entry.path());
    }

    // --- Appearance -------------------------------------------------------------------------------

    public void applyVisibilitySettings() {
        TurtleCommanderSettings.State settings = TurtleCommanderSettings.getInstance().getState();
        myDriveCombo.getComponent().setVisible(!settings.hideDriveSelector);
        myStatusPanel.setVisible(!settings.hideStatusBar);
        myEnableFileNameHighlighting = settings.enableFileNameHighlighting;
        // Polling for removable media is pointless while the selector is hidden.
        if (settings.hideDriveSelector) {
            stopDriveUpdates();
        }
        else {
            startDriveUpdates();
        }
    }

    private void startDriveUpdates() {
        if (myUnsubscribeDriveRoots != null) {
            return;
        }
        myUnsubscribeDriveRoots = myFileOps.subscribeToRoots(
            roots -> myDriveCombo.acceptRoots(roots, myCurrentPath));
    }

    private void stopDriveUpdates() {
        if (myUnsubscribeDriveRoots != null) {
            myUnsubscribeDriveRoots.run();
            myUnsubscribeDriveRoots = null;
        }
        myDriveCombo.clearPending();
    }

    public void showDriveSelector() {
        myDriveCombo.getComponent().requestFocusInWindow();
        myDriveCombo.getComponent().showPopup();
    }

    /** Collects the volume list off the UI thread — stat'ing a dead mount can block for seconds. */
    public void showDriveSpace() {
        UiTask.backgroundThenUi(DriveInfo::collectAll,
            drives -> new DriveSpaceDialog(myProject, drives).show());
    }

    public void setShowAllNestedFiles(boolean enabled) {
        if (myShowAllNestedFiles == enabled) {
            return;
        }
        myShowAllNestedFiles = enabled;
        UiTask.background(() -> FileTabNavigationUtil.navigateTo(this, myCurrentPath));
    }

    // --- Inline rename -------------------------------------------------------------------------------

    /** Installs the cell editor used for inline renames and binds it to the Name column. */
    public void installNameEditor() {
        JTextField field = new JTextField();
        TextFieldContextMenuUtil.installStandardContextMenu(field);

        DefaultCellEditor editor = new DefaultCellEditor(field) {
            @Override
            public Component getTableCellEditorComponent(JTable table, @Nullable Object value,
                                                         boolean isSelected, int row, int column) {
                myEditingEntry = myTableModel.getEntryAt(table.convertRowIndexToModel(row));
                // The cell renders a truncated or decorated name; editing must start from the
                // real one so an untouched edit doesn't rename the file to its display text.
                String fullName = myEditingEntry != null ? myEditingEntry.name()
                    : value != null ? value.toString() : "";
                return super.getTableCellEditorComponent(table, fullName, isSelected, row, column);
            }
        };
        // Clicks never start editing: the rename is driven by the arm-then-click gesture instead.
        editor.setClickCountToStart(Integer.MAX_VALUE);
        editor.addCellEditorListener(new CellEditorListener() {
            @Override
            public void editingStopped(ChangeEvent e) {
                FileEntry entry = myEditingEntry;
                myEditingEntry = null;
                if (entry == null || !(editor.getCellEditorValue() instanceof String value)) {
                    return;
                }
                String newName = value.trim();
                if (!newName.isEmpty() && !newName.equals(entry.name())) {
                    FileTabOperationUtil.performRename(FileTab.this, entry, newName);
                }
            }

            @Override
            public void editingCanceled(ChangeEvent e) {
                myEditingEntry = null;
            }
        });

        myNameEditor = editor;
        assignNameEditor();
    }

    /** Re-binds the editor after the column model is rebuilt, which drops cell editors. */
    public void assignNameEditor() {
        if (myNameEditor == null) {
            return;
        }
        TableColumnModel cm = myTable.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            if (cm.getColumn(i).getModelIndex() == FileTableModel.COL_NAME) {
                cm.getColumn(i).setCellEditor(myNameEditor);
                return;
            }
        }
    }

    public void cancelPendingRename() {
        if (myRenameClickTimer != null) {
            myRenameClickTimer.stop();
        }
    }

    public void disarmRename() {
        myRenameArmedRow = -1;
        cancelPendingRename();
    }

    /** See {@link #myRenameArmedRow} for the gesture this implements. */
    public void maybeArmOrStartRename(MouseEvent e) {
        if (!MouseGestureUtil.isPlainLeftClick(e)) {
            return;
        }
        int row = myTable.rowAtPoint(e.getPoint());
        int column = myTable.columnAtPoint(e.getPoint());
        boolean onNameCell = row >= 0 && column >= 0
            && myTable.convertColumnIndexToModel(column) == FileTableModel.COL_NAME;
        boolean soleSelection = myTable.getSelectedRowCount() == 1 && myTable.getSelectedRow() == row;

        if (onNameCell && soleSelection && row == myRenameArmedRow && canRenameRow(row)) {
            cancelPendingRename();
            myRenameClickTimer = new Timer(MouseGestureUtil.doubleClickInterval(), event -> {
                // Re-check: the user may have moved on — keyboard navigation, another click, a
                // view switch — during the deferral window.
                if (myViewMode == ViewMode.TABLE && myTable.getSelectedRowCount() == 1
                    && myTable.getSelectedRow() == row) {
                    FileTabOperationUtil.startRename(this);
                }
            });
            myRenameClickTimer.setRepeats(false);
            myRenameClickTimer.start();
        }
        myRenameArmedRow = soleSelection ? row : -1;
    }

    private boolean canRenameRow(int viewRow) {
        FileEntry entry = myTableModel.getEntryAt(myTable.convertRowIndexToModel(viewRow));
        return entry != null && !entry.parentLink();
    }

    /**
     * Pushes the configured panel font and colours into all four views, then re-applies the
     * dependent chrome styles. Falls back to the captured look-and-feel defaults wherever the
     * user has not overridden anything, so clearing a setting really restores the default.
     */
    public void applyPanelFont() {
        TurtleCommanderSettings settings = TurtleCommanderSettings.getInstance();
        Font font = settings.getPanelFont();
        int size = settings.getPanelFontSize();

        Font effectiveFont;
        int rowHeight;
        if (font != null) {
            effectiveFont = font;
            rowHeight = font.getSize() + 6;
        }
        else if (size > 0) {
            effectiveFont = myDefaultViewFont.deriveFont((float) size);
            rowHeight = size + 6;
        }
        else {
            effectiveFont = myDefaultViewFont;
            rowHeight = DEFAULT_ROW_HEIGHT;
        }

        myTable.setFont(effectiveFont);
        myTable.setRowHeight(rowHeight);
        myList.setFont(effectiveFont);
        myList.setFixedCellHeight(rowHeight);
        myThumbnailList.setFont(effectiveFont);
        myTree.setFont(effectiveFont);
        myTree.setRowHeight(rowHeight);

        ComponentStyle panelStyle = settings.getState().styles.panelStyle;
        Color foreground = orDefault(panelStyle.parsedFontColor(), myDefaultViewForeground);
        Color background = orDefault(panelStyle.parsedBackgroundColor(), myDefaultViewBackground);
        for (JComponent view : List.of(myTable, myList, myThumbnailList, myTree)) {
            view.setForeground(foreground);
            view.setBackground(background);
        }
        // The viewport shows through below the last row, so it has to match or the panel reads as
        // two different colours.
        if (myTable.getParent() instanceof JViewport viewport) {
            viewport.setBackground(background);
        }

        Color selection = orDefault(panelStyle.parsedActiveSelectedColor(), myDefaultSelectionBackground);
        myTable.setSelectionBackground(selection);
        myList.setSelectionBackground(selection);
        myThumbnailList.setSelectionBackground(selection);

        applyDriveSelectorStyle();
        applyColumnHeaderStyle();
        applyStatusBarStyle();
        myPathField.applyStyle(settings.getState().styles.pathBarStyle);
        FileTabColumnUtil.reapplyColumnConfig(this);
    }

    private static @Nullable Color orDefault(@Nullable Color color, @Nullable Color fallback) {
        return color != null ? color : fallback;
    }

    private void applyDriveSelectorStyle() {
        ComponentStyle style = TurtleCommanderSettings.getInstance().getState().styles.driveSelectorStyle;
        ComboBox<String> combo = myDriveCombo.getComponent();
        combo.setFont(orDefault(style.getFont(myDefaultDriveComboFont), myDefaultDriveComboFont));
        combo.setForeground(orDefault(style.parsedFontColor(), myDefaultDriveComboForeground));
        combo.setBackground(orDefault(style.parsedBackgroundColor(), myDefaultDriveComboBackground));
    }

    private void applyColumnHeaderStyle() {
        JTableHeader header = myTable.getTableHeader();
        if (header == null) {
            return;
        }
        ComponentStyle style = TurtleCommanderSettings.getInstance().getState().styles.columnHeaderStyle;
        header.setFont(orDefault(style.getFont(myDefaultHeaderFont), myDefaultHeaderFont));
        header.setForeground(orDefault(style.parsedFontColor(), myDefaultHeaderForeground));
        header.setBackground(orDefault(style.parsedBackgroundColor(), myDefaultHeaderBackground));
    }

    private void applyStatusBarStyle() {
        ComponentStyle style = TurtleCommanderSettings.getInstance().getState().styles.statusBarStyle;
        Font font = orDefault(style.getFont(myDefaultStatusFont), myDefaultStatusFont);
        Color foreground = orDefault(style.parsedFontColor(), myDefaultStatusForeground);
        Color background = orDefault(style.parsedBackgroundColor(), myDefaultStatusBackground);

        for (JLabel label : List.of(myStatusLabel, myFreeSpaceLabel)) {
            label.setFont(font);
            label.setForeground(foreground);
            label.setBackground(background);
        }
        myStatusPanel.setBackground(background);
    }

    private static @Nullable Font orDefault(@Nullable Font font, @Nullable Font fallback) {
        return font != null ? font : fallback;
    }

    public void applyThumbnailSettings() {
        ThumbnailSize size = ThumbnailSize.fromName(TurtleCommanderSettings.getInstance().getState().thumbnailSize);
        myThumbnailList.setFixedCellWidth(size.getCellWidth());
        myThumbnailList.setFixedCellHeight(size.getCellHeight());
        myThumbnailList.revalidate();
        myThumbnailList.repaint();
    }

    // --- Quick filter -----------------------------------------------------------------------------

    public void showQuickFilter() {
        if (myViewMode == ViewMode.TREE && !myShowAllNestedFiles) {
            // A flat glob doesn't map onto a lazily-loaded hierarchy, so refuse rather than showing
            // a bar that does nothing.
            myStatusLabel.setText("Quick filter is not available in the directory tree");
            return;
        }
        myQuickFilterBar.activate();
        revalidate();
    }

    public boolean isQuickFilterActive() {
        return myQuickFilterBar.isVisible();
    }

    public void hideQuickFilterIfActive() {
        if (myQuickFilterBar.isVisible()) {
            hideQuickFilter();
        }
    }

    /**
     * Drives the filter the way typing does — through {@link #showQuickFilter()}, so the
     * full-hierarchy-tree refusal still applies. A no-op when activation was refused.
     */
    public void setQuickFilterText(String text) {
        showQuickFilter();
        if (myQuickFilterBar.isVisible()) {
            myQuickFilterBar.setText(text);
        }
    }

    private void hideQuickFilter() {
        myQuickFilterBar.deactivate();
        // deactivate() fires no document event when the field was already empty, so re-apply
        // explicitly to guarantee the unfiltered listing is restored.
        applyFilter();
        revalidate();
        focusActiveView();
    }

    public void applyFilter() {
        QuickFilterMatcher.Result result = myFilterMatcher.filter(myAllEntries, myQuickFilterBar.getText());
        myQuickFilterBar.setError(result.invalidPattern());

        FileEntry selected = FileTabSelectionUtil.getSelectedEntry(this);
        String selectedName = selected != null ? selected.name() : null;

        myFilteredEntries = result.entries();
        FileTabViewUtil.setEntriesInAllViews(this, result.entries());
        FileTabSelectionUtil.reselectByName(this, selectedName);

        updateStatusBar();
    }

    // --- Selection / focus -------------------------------------------------------------------------

    public void focusActiveView() {
        activeViewComponent().requestFocusInWindow();
    }

    /** The component backing the current view mode. */
    public JComponent activeViewComponent() {
        return switch (myViewMode) {
            case TABLE -> myTable;
            case LIST -> myList;
            case THUMBNAIL -> myThumbnailList;
            case TREE -> myTree;
        };
    }

    /** True when keyboard focus is inside any of the four views owned by this tab. */
    public boolean hasAnyViewFocus() {
        return myTable.hasFocus() || myList.hasFocus() || myThumbnailList.hasFocus() || myTree.hasFocus();
    }

    public void moveSelection(int offset) {
        FileTabSelectionUtil.moveSelection(this, offset);
    }

    public void reSort() {
        if (myTable.getRowSorter() instanceof ParentPinningRowSorter sorter) {
            sorter.sort();
        }
    }

    // --- Status bar --------------------------------------------------------------------------------

    public void updateStatusBar() {
        List<FileEntry> entries = new ArrayList<>();
        for (int i = 0; i < myTableModel.getRowCount(); i++) {
            FileEntry entry = myTableModel.getEntryAt(i);
            if (entry != null) {
                entries.add(entry);
            }
        }

        myStatusLabel.setText(StatusBarTextUtil.summary(entries,
            FileTabSelectionUtil.getSelectedEntries(this), myDirectorySizes));

        Path path = myCurrentPath;
        if (!path.equals(myFreeSpaceLastPath)) {
            myFreeSpaceLastPath = path;
            UiTask.backgroundThenUi(() -> StatusBarTextUtil.freeSpace(path), myFreeSpaceLabel::setText);
        }
    }

    public void invalidateFreeSpaceCache() {
        myFreeSpaceLastPath = null;
    }

    // --- Column state -------------------------------------------------------------------------------

    public void saveColumnState() {
        myTabColumnStates.put(myCurrentPath.toString(), ColumnStateUtil.capture(myTable));
    }

    public void restoreColumnState(Path path) {
        ColumnStateUtil.ColumnState state = myTabColumnStates.get(path.toString());
        if (state != null) {
            ColumnStateUtil.apply(myTable, state.widths(), state.order());
        }
    }

    // --- Persisted tab state -------------------------------------------------------------------------

    public TabState saveTabState() {
        ColumnStateUtil.ColumnState columns = ColumnStateUtil.capture(myTable);

        int sortColumn = -1;
        boolean sortAscending = true;
        RowSorter<?> sorter = myTable.getRowSorter();
        if (sorter != null && !sorter.getSortKeys().isEmpty()) {
            RowSorter.SortKey key = sorter.getSortKeys().get(0);
            sortColumn = key.getColumn();
            sortAscending = key.getSortOrder() != SortOrder.DESCENDING;
        }

        // Inside an archive, persist the directory holding the outermost archive: reopening into a
        // half-extracted temp dir on the next start would point at nothing.
        String persistPath;
        if (myVfsStack.isEmpty()) {
            persistPath = myCurrentPath.toString();
        }
        else {
            Path archivePath = myVfsStack.get(0).getParentPath();
            Path parent = archivePath.getParent();
            persistPath = (parent != null ? parent : archivePath).toString();
        }

        TabState state = new TabState(persistPath, myViewMode.name());
        state.columnWidths = ColumnStateUtil.encode(columns.widths());
        state.columnOrder = ColumnStateUtil.encode(columns.order());
        state.sortColumn = sortColumn;
        state.sortAscending = sortAscending;
        state.showAllNestedFiles = myShowAllNestedFiles;
        return state;
    }

    public void restoreTabState(TabState state) {
        List<Integer> widths = ColumnStateUtil.decode(state.columnWidths);
        List<Integer> order = ColumnStateUtil.decode(state.columnOrder);
        if (!widths.isEmpty() && !order.isEmpty()) {
            ColumnStateUtil.apply(myTable, widths, order);
        }
        RowSorter<?> sorter = myTable.getRowSorter();
        if (state.sortColumn >= 0 && sorter != null) {
            SortOrder sortOrder = state.sortAscending ? SortOrder.ASCENDING : SortOrder.DESCENDING;
            sorter.setSortKeys(List.of(new RowSorter.SortKey(state.sortColumn, sortOrder)));
        }
    }

    public boolean hasTabColumnState(String path) {
        return myTabColumnStates.containsKey(path);
    }

    /**
     * Test seam: drops the in-memory per-path column state. Navigation calls
     * {@link #saveColumnState()}, so by the time a test has a settled tab the "no saved state"
     * branch of the view switch is unreachable; clearing makes it deterministic.
     */
    public void clearColumnState() {
        myTabColumnStates.clear();
    }

    // --- Lifecycle ------------------------------------------------------------------------------------

    @Override
    public void dispose() {
        cancelPendingRename();
        stopDriveUpdates();
        FileTabVfsUtil.closeVfsStack(this);
    }
}
