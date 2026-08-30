package consulo.turtlecommander.ui;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.turtlecommander.action.ActionTargets;
import consulo.turtlecommander.action.TabContextMenuState;
import consulo.turtlecommander.search.FileSearchCriteria;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.service.PanelState;
import consulo.turtlecommander.service.TabState;
import consulo.turtlecommander.settings.ComponentStyle;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.util.UiTask;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionPopupMenu;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.awt.JBTabbedPane;
import consulo.ui.ex.awt.JBUI;
import org.jspecify.annotations.Nullable;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * One side of the dual-pane view: a tab strip of {@link FileTab}s (plus any search-result tabs),
 * the view-mode toggle overlay, and the tab drag-and-drop that can move a tab to the other panel.
 */
public class FileManagerPanel extends JPanel {
    private static final int CLOSED_TABS_LIMIT = 32;
    private static final int DRAG_THRESHOLD_PX = 5;

    private final Project myProject;
    private final Path myInitialPath;
    private final Supplier<@Nullable Path> myOtherPanelPathProvider;

    private final JBTabbedPane myTabbedPane = new JBTabbedPane();
    private final JPanel myAddTabPlaceholder = new JPanel();
    private final Font myDefaultTabFont = myTabbedPane.getFont();
    private final @Nullable Color myDefaultTabBackground = UIManager.getColor("TabbedPane.background");

    private final ViewModeButton myTableViewButton =
        new ViewModeButton(PlatformIconGroup.actionsPreviewdetails(), "Table view", true);
    private final ViewModeButton myListViewButton =
        new ViewModeButton(PlatformIconGroup.actionsListfiles(), "List view", false);
    private final ViewModeButton myThumbnailViewButton =
        new ViewModeButton(PlatformIconGroup.actionsChangeview(), "Thumbnail view", false);
    private final ViewModeButton myTreeViewButton =
        new ViewModeButton(PlatformIconGroup.actionsShowastree(), "Tree view", false);
    private final ViewModeButton myNestedFilesButton =
        new ViewModeButton(PlatformIconGroup.objectbrowserFlattenmodules(), "Show all nested files", false);
    private final JPanel myViewTogglePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));

    private @Nullable FileManagerPanel myOtherPanel;
    private @Nullable FileManagerStateService myStateService;

    /**
     * Parent for the {@link Disposer} registration of every tab this panel creates — the tool
     * window's own disposable. Without it a tab is only disposed when the user closes it, so a tab
     * left inside an archive at project close would keep its filesystem handles open and leak its
     * extraction temp directory.
     */
    private @Nullable Disposable myParentDisposable;

    private int myDragSourceIndex = -1;
    private int myDropTargetIndex = -1;
    private boolean myDragActive;
    private @Nullable Point myDragStartPoint;

    /** Enough state to put a closed tab back where it was. */
    private record ClosedTabEntry(TabState state, int index) {
    }

    private final Deque<ClosedTabEntry> myClosedTabs = new ArrayDeque<>();

    public FileManagerPanel(Project project, Path initialPath,
                            Supplier<@Nullable Path> otherPanelPathProvider) {
        super(new BorderLayout());
        myProject = project;
        myInitialPath = initialPath;
        myOtherPanelPathProvider = otherPanelPathProvider;

        appendPlusTab();
        installTabListeners();
        buildViewTogglePanel();

        DraggableTabbedPaneWrapper tabbedPaneWrapper = new DraggableTabbedPaneWrapper(myTabbedPane, this);

        JPanel topRight = new JPanel(new BorderLayout());
        topRight.setOpaque(false);
        topRight.add(myViewTogglePanel, BorderLayout.EAST);

        JPanel toggleOverlay = new JPanel(new BorderLayout());
        toggleOverlay.setOpaque(false);
        toggleOverlay.add(topRight, BorderLayout.NORTH);
        toggleOverlay.setAlignmentX(0.5f);
        toggleOverlay.setAlignmentY(0.5f);
        tabbedPaneWrapper.setAlignmentX(0.5f);
        tabbedPaneWrapper.setAlignmentY(0.5f);

        JPanel layeredWrapper = new JPanel();
        layeredWrapper.setOpaque(false);
        layeredWrapper.setLayout(new OverlayLayout(layeredWrapper));
        layeredWrapper.add(toggleOverlay);
        layeredWrapper.add(tabbedPaneWrapper);

        add(layeredWrapper, BorderLayout.CENTER);
        applyTabbedPaneStyle();
    }

    public void setOtherPanel(@Nullable FileManagerPanel otherPanel) {
        myOtherPanel = otherPanel;
    }

    public @Nullable FileManagerPanel getOtherPanel() {
        return myOtherPanel;
    }

    public void setParentDisposable(@Nullable Disposable parentDisposable) {
        myParentDisposable = parentDisposable;
    }

    // --- Tab strip wiring ---------------------------------------------------------------------------

    private void appendPlusTab() {
        myTabbedPane.addTab("", myAddTabPlaceholder);
        myTabbedPane.setTabComponentAt(plusIndex(), TabStripButtons.newTab(this::openNewTab));
    }

    private int plusIndex() {
        return myTabbedPane.indexOfComponent(myAddTabPlaceholder);
    }

    private void installTabListeners() {
        myTabbedPane.addChangeListener(e -> {
            // The "+" tab is a button, not a page: bounce the selection back off it.
            int plus = plusIndex();
            if (myTabbedPane.getSelectedIndex() == plus && plus > 0) {
                myTabbedPane.setSelectedIndex(plus - 1);
            }
            syncViewToggle();
            FileTab tab = getActiveTab();
            if (tab != null) {
                FileTabNavigationUtil.revalidateIfStale(tab);
            }
        });

        myTabbedPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    int tabIndex = getTabIndexAt(e.getPoint());
                    if (tabIndex >= 0 && tabIndex != plusIndex()) {
                        closeTab(tabIndex);
                    }
                    return;
                }
                if (e.isPopupTrigger() || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int tabIndex = getTabIndexAt(e.getPoint());
                int plus = plusIndex();
                if (tabIndex == plus && plus >= 0) {
                    openNewTab();
                    return;
                }
                if (tabIndex >= 0 && getRealTabCount() > 1) {
                    myDragSourceIndex = tabIndex;
                    myDragStartPoint = e.getPoint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
                if (myDragActive && myDragSourceIndex >= 0) {
                    performDrop(e);
                }
                myDragSourceIndex = -1;
                myDropTargetIndex = -1;
                myDragActive = false;
                myDragStartPoint = null;
                myTabbedPane.repaint();
            }
        });

        myTabbedPane.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                handleDrag(e);
            }
        });
    }

    private void handlePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int tabIndex = getTabIndexAt(e.getPoint());
        if (tabIndex < 0 || tabIndex == plusIndex()) {
            return;
        }

        TabContextMenuState.setClickedTabIndex(tabIndex);
        TabContextMenuState.setClickedPanel(this);

        ActionManager actionManager = ActionManager.getInstance();
        AnAction action = actionManager.getAction("TurtleCommander.TabContextMenu");
        if (!(action instanceof ActionGroup group)) {
            TabContextMenuState.clear();
            return;
        }
        consulo.ui.Component target = TargetAWT.wrap(myTabbedPane);
        ActionPopupMenu popupMenu =
            actionManager.createActionPopupMenu(ActionTargets.TAB_CONTEXT_PLACE, group);
        popupMenu.setTargetComponent(target);
        popupMenu.show(target, e.getX(), e.getY());
    }

    private void handleDrag(MouseEvent e) {
        if (myDragSourceIndex < 0 || myDragStartPoint == null) {
            return;
        }
        if (!myDragActive && myDragStartPoint.distance(e.getPoint()) < DRAG_THRESHOLD_PX) {
            return;
        }
        myDragActive = true;
        myTabbedPane.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        Point screenPoint = e.getLocationOnScreen();
        FileManagerPanel other = myOtherPanel;
        if (other != null && other.myTabbedPane.isShowing()) {
            Point otherLocation = other.myTabbedPane.getLocationOnScreen();
            Rectangle otherBounds = new Rectangle(otherLocation.x, otherLocation.y,
                other.myTabbedPane.getWidth(), other.myTabbedPane.getHeight());
            if (otherBounds.contains(screenPoint)) {
                other.updateDropIndicator(new Point(screenPoint.x - otherLocation.x,
                    screenPoint.y - otherLocation.y));
                myDropTargetIndex = -1;
                myTabbedPane.repaint();
                return;
            }
            other.clearDropIndicator();
        }

        int plus = plusIndex();
        int tabIndex = getTabIndexAt(e.getPoint());
        if (tabIndex >= 0 && tabIndex != plus && tabIndex != myDragSourceIndex) {
            myDropTargetIndex = tabIndex;
        }
        else if (tabIndex == plus || (tabIndex < 0 && e.getPoint().x > 0)) {
            // Dropping on or past the "+" tab means "append", which is a no-op when the dragged
            // tab is already the last one.
            myDropTargetIndex = myDragSourceIndex != plus - 1 ? plus : -1;
        }
        else {
            myDropTargetIndex = -1;
        }
        myTabbedPane.repaint();
    }

    private void updateDropIndicator(Point localPoint) {
        int plus = plusIndex();
        int tabIndex = getTabIndexAt(localPoint);
        myDropTargetIndex = tabIndex >= 0 && tabIndex != plus ? tabIndex : plus;
        myTabbedPane.repaint();
    }

    private void clearDropIndicator() {
        if (myDropTargetIndex >= 0) {
            myDropTargetIndex = -1;
            myTabbedPane.repaint();
        }
    }

    private void performDrop(MouseEvent e) {
        myTabbedPane.setCursor(Cursor.getDefaultCursor());

        Point screenPoint = e.getLocationOnScreen();
        FileManagerPanel other = myOtherPanel;
        if (other != null && other.myTabbedPane.isShowing()) {
            Point otherLocation = other.myTabbedPane.getLocationOnScreen();
            Rectangle otherBounds = new Rectangle(otherLocation.x, otherLocation.y,
                other.myTabbedPane.getWidth(), other.myTabbedPane.getHeight());
            if (otherBounds.contains(screenPoint)) {
                int targetIdx = other.myDropTargetIndex;
                other.clearDropIndicator();
                moveTabToOtherPanel(myDragSourceIndex, other, targetIdx);
                return;
            }
        }
        if (myDropTargetIndex >= 0 && myDropTargetIndex != myDragSourceIndex) {
            reorderTab(myDragSourceIndex, myDropTargetIndex);
        }
    }

    public @Nullable Rectangle getDropIndicatorRect() {
        if (myDropTargetIndex < 0) {
            return null;
        }
        Rectangle bounds = myTabbedPane.getBoundsAt(myDropTargetIndex);
        return bounds != null ? new Rectangle(bounds.x, bounds.y, 2, bounds.height) : null;
    }

    public void reorderTab(int fromIndex, int toIndex) {
        Component component = myTabbedPane.getComponentAt(fromIndex);
        String title = myTabbedPane.getTitleAt(fromIndex);
        Component tabComponent = myTabbedPane.getTabComponentAt(fromIndex);

        // JBTabbedPane.insertTab adds insets to the component's border; snapshot and restore it so
        // repeated reorders don't accumulate padding.
        Border savedBorder = component instanceof JComponent jc ? jc.getBorder() : null;

        myTabbedPane.removeTabAt(fromIndex);
        int adjustedTo = fromIndex < toIndex ? toIndex - 1 : toIndex;
        myTabbedPane.insertTab(title, (Icon) null, component, null, adjustedTo);
        if (component instanceof JComponent jc) {
            jc.setBorder(savedBorder);
        }
        myTabbedPane.setTabComponentAt(adjustedTo, tabComponent);
        myTabbedPane.setSelectedIndex(adjustedTo);
    }

    public void moveTabToOtherPanel(int sourceIndex, FileManagerPanel target, int targetIndex) {
        if (!(myTabbedPane.getComponentAt(sourceIndex) instanceof FileTab fileTab)) {
            return;
        }
        // A panel must always keep at least one tab.
        if (getRealTabCount() <= 1) {
            return;
        }
        Path path = fileTab.getCurrentPath();
        myTabbedPane.removeTabAt(sourceIndex);

        int targetPlus = target.plusIndex();
        int insertAt = targetIndex >= 0 && targetIndex < targetPlus ? targetIndex : targetPlus;
        String title = titleFor(path);

        Border savedBorder = fileTab.getBorder();
        target.myTabbedPane.insertTab(title, (Icon) null, fileTab, null, insertAt);
        fileTab.setBorder(savedBorder);

        target.myTabbedPane.setTabComponentAt(insertAt, target.createTabHeader(title, fileTab));
        target.myTabbedPane.setSelectedIndex(insertAt);

        target.wireTabCallbacks(fileTab);
    }

    private void wireTabCallbacks(FileTab fileTab) {
        fileTab.updatePanelCallbacks(this::otherPanelPath, tab -> {
            updateTabTitle(tab);
            syncViewToggle();
        }, this::refreshOtherPanel);
    }

    private @Nullable Path otherPanelPath() {
        FileManagerPanel other = myOtherPanel;
        FileTab otherTab = other != null ? other.getActiveTab() : null;
        return otherTab != null ? otherTab.getRealFilesystemPath() : myOtherPanelPathProvider.get();
    }

    private void refreshOtherPanel() {
        FileManagerPanel other = myOtherPanel;
        if (other != null) {
            other.refreshActiveTab(false);
        }
    }

    // --- Tab creation and removal --------------------------------------------------------------------

    public void openNewTab() {
        FileTab currentTab = getActiveTab();
        addNewTab(currentTab != null ? currentTab.getCurrentPath() : myInitialPath, null, null, -1);
        focusActiveTab();
    }

    public void openDirectoryInNewTab(Path path, @Nullable String selectName) {
        addNewTab(path, selectName, null, -1);
        focusActiveTab();
    }

    public SearchResultsPanel openSearchTab(FileSearchCriteria criteria) {
        SearchResultsPanel searchPanel = new SearchResultsPanel(myProject, criteria);
        if (myParentDisposable != null) {
            Disposer.register(myParentDisposable, searchPanel);
        }

        int insertIndex = plusIndex() >= 0 ? plusIndex() : myTabbedPane.getTabCount();
        String title = "Search: " + searchTabLabel(criteria);
        myTabbedPane.insertTab(title, (Icon) null, searchPanel, null, insertIndex);
        myTabbedPane.setTabComponentAt(insertIndex, createSearchTabHeader(title, searchPanel));
        myTabbedPane.setSelectedIndex(insertIndex);

        searchPanel.startSearch();
        return searchPanel;
    }

    private static String searchTabLabel(FileSearchCriteria criteria) {
        if (criteria.namePattern() != null) {
            return criteria.namePattern();
        }
        Path fileName = criteria.rootPath().getFileName();
        return fileName != null ? fileName.toString() : "Results";
    }

    void closeSearchTab(SearchResultsPanel searchPanel) {
        Disposer.dispose(searchPanel);
        int index = myTabbedPane.indexOfComponent(searchPanel);
        if (index < 0) {
            return;
        }
        myTabbedPane.removeTabAt(index);
        if (getRealTabCount() == 0) {
            addNewTab(myInitialPath, null, null, -1);
        }
        focusActiveTab();
    }

    /**
     * @param insertIndex position in the strip, or -1 to append just before the "+" tab
     * @param tabState    persisted state to apply on the first listing; when null, a new tab inherits
     *                    the active tab's column layout so the strip stays visually consistent
     */
    private void addNewTab(Path path, @Nullable String selectName, @Nullable TabState tabState,
                           int insertIndex) {
        TabState effectiveState = tabState != null ? tabState : inheritedTabState();

        FileTab fileTab = new FileTab(myProject, path, this::otherPanelPath, tab -> {
            updateTabTitle(tab);
            syncViewToggle();
        }, this::refreshOtherPanel);
        if (myParentDisposable != null) {
            Disposer.register(myParentDisposable, fileTab);
        }
        fileTab.initializeViews();

        int maxInsert = plusIndex() >= 0 ? plusIndex() : myTabbedPane.getTabCount();
        int resolvedIndex = insertIndex >= 0 ? Math.clamp(insertIndex, 0, maxInsert) : maxInsert;
        String title = titleFor(path);
        myTabbedPane.insertTab(title, (Icon) null, fileTab, null, resolvedIndex);
        myTabbedPane.setTabComponentAt(resolvedIndex, createTabHeader(title, fileTab));
        myTabbedPane.setSelectedIndex(resolvedIndex);

        if (myStateService != null) {
            fileTab.setStateService(myStateService);
            if (effectiveState != null) {
                fileTab.setPendingTabState(effectiveState);
                fileTab.setShowAllNestedFilesField(effectiveState.showAllNestedFiles);
                FileTabViewUtil.setViewMode(fileTab, ViewMode.fromName(effectiveState.viewMode));
            }
        }

        UiTask.background(() -> FileTabNavigationUtil.navigateTo(fileTab, path, selectName, true));
    }

    /** Only the table layout is worth inheriting; the other views have no per-tab geometry. */
    private @Nullable TabState inheritedTabState() {
        FileTab active = getActiveTab();
        if (active == null || active.getViewMode() != ViewMode.TABLE) {
            return null;
        }
        return active.saveTabState();
    }

    public void closeTab(int tabIndex) {
        if (getRealTabCount() <= 1 || tabIndex < 0 || tabIndex == plusIndex()) {
            return;
        }
        removeTabsAt(List.of(tabIndex));
        focusActiveTab();
    }

    /**
     * Pushes each index's state onto the closed-tab stack, then disposes and removes in descending
     * order so earlier removals don't shift later indices. Focus and selection are the caller's.
     */
    private void removeTabsAt(List<Integer> indices) {
        if (indices.isEmpty()) {
            return;
        }
        for (int index : indices) {
            rememberClosedTab(myTabbedPane.getComponentAt(index), index);
        }
        List<Integer> descending = new ArrayList<>(indices);
        descending.sort((a, b) -> Integer.compare(b, a));
        for (int index : descending) {
            Component component = myTabbedPane.getComponentAt(index);
            // Disposing through the Disposer (rather than calling dispose() directly) also detaches
            // the tab from the tool window's disposable, so it isn't disposed twice at project close.
            if (component instanceof Disposable disposable) {
                Disposer.dispose(disposable);
            }
            myTabbedPane.removeTabAt(index);
        }
    }

    private void rememberClosedTab(@Nullable Component component, int tabIndex) {
        if (!(component instanceof FileTab fileTab)) {
            return;
        }
        myClosedTabs.addLast(new ClosedTabEntry(fileTab.saveTabState(), tabIndex));
        while (myClosedTabs.size() > CLOSED_TABS_LIMIT) {
            myClosedTabs.removeFirst();
        }
    }

    public boolean hasClosedTabs() {
        return !myClosedTabs.isEmpty();
    }

    public void reopenLastClosedTab() {
        ClosedTabEntry entry = myClosedTabs.pollLast();
        if (entry == null) {
            return;
        }
        Path path;
        try {
            path = Path.of(entry.state().path);
        }
        catch (Exception e) {
            return;
        }
        addNewTab(path, null, entry.state(), entry.index());
        focusActiveTab();
    }

    public void closeOtherTabs(int tabIndex) {
        int plus = plusIndex();
        if (tabIndex < 0 || tabIndex == plus) {
            return;
        }
        // Ascending order pushes the rightmost tab last, so it reopens first and the original
        // left-to-right layout is restored.
        removeTabsAt(indicesExcept(index -> index == tabIndex || index == plus));
    }

    public void closeAllTabs() {
        int plus = plusIndex();
        int firstReal = -1;
        for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
            if (i != plus) {
                firstReal = i;
                break;
            }
        }
        if (firstReal < 0) {
            return;
        }
        int keep = firstReal;
        removeTabsAt(indicesExcept(index -> index == keep || index == plus));
    }

    public void closeTabsToTheLeft(int tabIndex) {
        int plus = plusIndex();
        if (tabIndex <= 0 || tabIndex == plus) {
            return;
        }
        removeTabsAt(indicesExcept(index -> index >= tabIndex || index == plus));
    }

    public void closeTabsToTheRight(int tabIndex) {
        int plus = plusIndex();
        if (tabIndex < 0 || tabIndex == plus) {
            return;
        }
        removeTabsAt(indicesExcept(index -> index <= tabIndex || index == plus));
    }

    public void duplicateTab(int tabIndex) {
        if (tabIndex < 0 || tabIndex == plusIndex()
            || !(myTabbedPane.getComponentAt(tabIndex) instanceof FileTab tab)) {
            return;
        }
        addNewTab(tab.getCurrentPath(), null, tab.saveTabState(), -1);
        focusActiveTab();
    }

    public boolean hasDuplicateTabs(int tabIndex) {
        int plus = plusIndex();
        if (tabIndex < 0 || tabIndex == plus
            || !(myTabbedPane.getComponentAt(tabIndex) instanceof FileTab tab)) {
            return false;
        }
        Path path = tab.getCurrentPath();
        for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
            if (i != tabIndex && i != plus
                && myTabbedPane.getComponentAt(i) instanceof FileTab other
                && other.getCurrentPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    public void closeDuplicateTabs(int tabIndex) {
        int plus = plusIndex();
        if (tabIndex < 0 || tabIndex == plus
            || !(myTabbedPane.getComponentAt(tabIndex) instanceof FileTab keep)) {
            return;
        }
        Path keepPath = keep.getCurrentPath();
        removeTabsAt(indicesExcept(index -> index == tabIndex || index == plus,
            index -> myTabbedPane.getComponentAt(index) instanceof FileTab other
                && other.getCurrentPath().equals(keepPath)));
        focusActiveTab();
    }

    private List<Integer> indicesExcept(IntPredicate excluded) {
        return indicesExcept(excluded, index -> true);
    }

    private List<Integer> indicesExcept(IntPredicate excluded, IntPredicate included) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
            if (!excluded.test(i) && included.test(i)) {
                indices.add(i);
            }
        }
        return indices;
    }

    // --- Tab headers ---------------------------------------------------------------------------------

    JPanel createTabHeader(String title, FileTab fileTab) {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.setOpaque(false);

        JLabel label = new JLabel(title);
        applyTabStyleToHeader(panel, label);
        panel.add(label, BorderLayout.CENTER);

        // The header sits on top of the tab strip and swallows its mouse events, so forward them:
        // without this, clicking or dragging a tab by its label does nothing.
        MouseAdapter forwarder = new MouseAdapter() {
            private void forward(MouseEvent e) {
                myTabbedPane.dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, myTabbedPane));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                forward(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                forward(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                forward(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                forward(e);
            }
        };
        panel.addMouseListener(forwarder);
        label.addMouseListener(forwarder);
        panel.addMouseMotionListener(forwarder);
        label.addMouseMotionListener(forwarder);

        panel.add(TabStripButtons.closeTab(() -> {
            int index = myTabbedPane.indexOfComponent(fileTab);
            if (index >= 0) {
                closeTab(index);
            }
        }), BorderLayout.EAST);
        return panel;
    }

    private JPanel createSearchTabHeader(String title, SearchResultsPanel searchPanel) {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.setOpaque(false);
        JLabel label = new JLabel(title);
        applyTabStyleToHeader(panel, label);
        panel.add(label, BorderLayout.CENTER);
        panel.add(TabStripButtons.closeTab(() -> closeSearchTab(searchPanel)), BorderLayout.EAST);
        return panel;
    }

    private static void applyTabStyleToHeader(JPanel panel, JLabel label) {
        TurtleCommanderSettings settings = TurtleCommanderSettings.getInstance();
        ComponentStyle tabStyle = settings.getState().styles.tabStyle;
        Font font = settings.getTabFont();
        if (font != null) {
            label.setFont(font);
        }
        Color foreground = tabStyle.parsedFontColor();
        if (foreground != null) {
            label.setForeground(foreground);
        }
        Color background = tabStyle.parsedBackgroundColor();
        if (background != null) {
            panel.setOpaque(true);
            panel.setBackground(background);
        }
    }

    private void updateTabTitle(FileTab tab) {
        int index = myTabbedPane.indexOfComponent(tab);
        if (index < 0) {
            return;
        }
        String title = titleFor(tab.getCurrentPath());
        myTabbedPane.setTitleAt(index, title);
        if (myTabbedPane.getTabComponentAt(index) instanceof JPanel header
            && header.getComponentCount() > 0
            && header.getComponent(0) instanceof JLabel label) {
            label.setText(title);
        }
    }

    private static String titleFor(Path path) {
        Path fileName = path.getFileName();
        return fileName != null ? fileName.toString() : path.toString();
    }

    // --- View toggle ----------------------------------------------------------------------------------

    private void buildViewTogglePanel() {
        myViewTogglePanel.setOpaque(false);
        myViewTogglePanel.setBorder(JBUI.Borders.empty(8, 0, 0, 8));

        ButtonGroup group = new ButtonGroup();
        group.add(myTableViewButton);
        group.add(myListViewButton);
        group.add(myThumbnailViewButton);
        group.add(myTreeViewButton);
        myViewTogglePanel.add(myTableViewButton);
        myViewTogglePanel.add(myListViewButton);
        myViewTogglePanel.add(myThumbnailViewButton);
        myViewTogglePanel.add(myTreeViewButton);
        myViewTogglePanel.add(Box.createHorizontalStrut(12));
        myViewTogglePanel.add(myNestedFilesButton);

        myNestedFilesButton.addActionListener(e -> withActiveTab(
            tab -> tab.setShowAllNestedFiles(myNestedFilesButton.isSelected())));
        myTableViewButton.addActionListener(e -> withActiveTab(
            tab -> FileTabViewUtil.setViewMode(tab, ViewMode.TABLE)));
        myListViewButton.addActionListener(e -> withActiveTab(
            tab -> FileTabViewUtil.setViewMode(tab, ViewMode.LIST)));
        myThumbnailViewButton.addActionListener(e -> withActiveTab(
            tab -> FileTabViewUtil.setViewMode(tab, ViewMode.THUMBNAIL)));
        myTreeViewButton.addActionListener(e -> withActiveTab(
            tab -> FileTabViewUtil.setViewMode(tab, ViewMode.TREE)));
    }

    private void withActiveTab(Consumer<FileTab> action) {
        FileTab tab = getActiveTab();
        if (tab != null) {
            action.accept(tab);
        }
    }

    private void syncViewToggle() {
        if (myTabbedPane.getSelectedComponent() instanceof SearchResultsPanel) {
            myViewTogglePanel.setVisible(false);
            return;
        }
        myViewTogglePanel.setVisible(true);

        FileTab tab = getActiveTab();
        ViewMode mode = tab != null ? tab.getViewMode() : ViewMode.TABLE;
        switch (mode) {
            case LIST -> myListViewButton.setSelected(true);
            case THUMBNAIL -> myThumbnailViewButton.setSelected(true);
            case TREE -> myTreeViewButton.setSelected(true);
            default -> myTableViewButton.setSelected(true);
        }
        myNestedFilesButton.setSelected(tab != null && tab.isShowAllNestedFiles());
    }

    // --- Queries and navigation -------------------------------------------------------------------------

    public @Nullable FileTab getActiveTab() {
        Component selected = myTabbedPane.getSelectedComponent();
        return selected instanceof FileTab tab ? tab : null;
    }

    public @Nullable FileTab getTabAt(int index) {
        return myTabbedPane.getComponentAt(index) instanceof FileTab tab ? tab : null;
    }

    public int getActiveTabIndex() {
        return myTabbedPane.getSelectedIndex();
    }

    public int getTabIndexAt(Point point) {
        for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
            Rectangle bounds = myTabbedPane.getBoundsAt(i);
            if (bounds != null && bounds.contains(point)) {
                return i;
            }
        }
        return -1;
    }

    /** Excludes the "+" tab. */
    public int getRealTabCount() {
        return myTabbedPane.getTabCount() - 1;
    }

    public void selectNextTab() {
        if (getRealTabCount() <= 1) {
            return;
        }
        int next = myTabbedPane.getSelectedIndex() + 1;
        myTabbedPane.setSelectedIndex(next >= plusIndex() ? 0 : next);
        focusActiveTab();
    }

    public void selectPreviousTab() {
        if (getRealTabCount() <= 1) {
            return;
        }
        int previous = myTabbedPane.getSelectedIndex() - 1;
        myTabbedPane.setSelectedIndex(previous < 0 ? plusIndex() - 1 : previous);
        focusActiveTab();
    }

    public void focusActiveTab() {
        if (myTabbedPane.getSelectedComponent() instanceof SearchResultsPanel searchPanel) {
            searchPanel.requestTableFocus();
            return;
        }
        FileTab tab = getActiveTab();
        if (tab != null) {
            tab.focusActiveView();
        }
    }

    public boolean hasFocusInPanel() {
        if (myTabbedPane.getSelectedComponent() instanceof SearchResultsPanel searchPanel) {
            return searchPanel.hasTableFocus();
        }
        FileTab tab = getActiveTab();
        return tab != null && tab.hasAnyViewFocus();
    }

    public void refreshActiveTab(boolean requestFocus) {
        FileTab tab = getActiveTab();
        if (tab != null) {
            FileTabNavigationUtil.refresh(tab, requestFocus);
        }
    }

    public void refreshActiveTab(String selectName, boolean requestFocus) {
        FileTab tab = getActiveTab();
        if (tab != null) {
            UiTask.background(() ->
                FileTabNavigationUtil.navigateTo(tab, tab.getCurrentPath(), selectName, requestFocus));
        }
    }

    public void showDriveSelector() {
        withActiveTab(FileTab::showDriveSelector);
    }

    // --- Settings propagation -----------------------------------------------------------------------------

    private void applyTabbedPaneStyle() {
        TurtleCommanderSettings settings = TurtleCommanderSettings.getInstance();
        Font tabFont = settings.getTabFont();
        if (tabFont != null) {
            myTabbedPane.setFont(tabFont);
        }
        Color background = settings.getState().styles.tabStyle.parsedBackgroundColor();
        if (background != null) {
            myTabbedPane.setBackground(background);
        }
    }

    public void applyFonts() {
        TurtleCommanderSettings settings = TurtleCommanderSettings.getInstance();
        ComponentStyle tabStyle = settings.getState().styles.tabStyle;
        Font tabFont = settings.getTabFont();
        int tabFontSize = settings.getTabFontSize();

        Font effectiveFont = tabFont != null ? tabFont
            : tabFontSize > 0 ? myDefaultTabFont.deriveFont((float) tabFontSize)
            : myDefaultTabFont;
        myTabbedPane.setFont(effectiveFont);

        Color foreground = tabStyle.parsedFontColor();
        Color background = tabStyle.parsedBackgroundColor();
        myTabbedPane.setBackground(background != null ? background : myDefaultTabBackground);

        int plus = plusIndex();
        for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
            if (i == plus || !(myTabbedPane.getTabComponentAt(i) instanceof JPanel header)) {
                continue;
            }
            header.setOpaque(background != null);
            if (background != null) {
                header.setBackground(background);
            }
            if (header.getComponentCount() > 0 && header.getComponent(0) instanceof JLabel label) {
                label.setFont(effectiveFont);
                // Null restores inheritance from the L&F rather than pinning the previous colour.
                label.setForeground(foreground);
            }
        }
        forEachTab(FileTab::applyPanelFont);
    }

    public void applyVisibilitySettings() {
        forEachTab(FileTab::applyVisibilitySettings);
    }

    public void applyThumbnailSettings() {
        forEachTab(FileTab::applyThumbnailSettings);
    }

    public void reSort() {
        forEachTab(FileTab::reSort);
    }

    private void forEachTab(Consumer<FileTab> action) {
        for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
            if (myTabbedPane.getComponentAt(i) instanceof FileTab tab) {
                action.accept(tab);
            }
        }
    }

    // --- Persistence ------------------------------------------------------------------------------------

    public void restoreState(PanelState panelState, FileManagerStateService stateService) {
        myStateService = stateService;

        // Only parse-check the saved paths: this runs on the UI thread during tool-window creation,
        // and stat'ing each one can block for seconds on a stale network share. A tab whose
        // directory no longer exists is handled by its initial navigation, which falls back to the
        // nearest surviving ancestor off-thread.
        List<TabState> valid = new ArrayList<>();
        List<Path> paths = new ArrayList<>();
        for (TabState tabState : panelState.tabs) {
            try {
                paths.add(Path.of(tabState.path));
                valid.add(tabState);
            }
            catch (Exception e) {
                // Unparseable path — drop the tab rather than failing the whole restore.
            }
        }

        if (valid.isEmpty()) {
            addNewTab(myInitialPath, null, null, -1);
        }
        else {
            for (int i = 0; i < valid.size(); i++) {
                addNewTab(paths.get(i), null, valid.get(i), -1);
            }
            myTabbedPane.setSelectedIndex(
                Math.clamp(panelState.selectedTabIndex, 0, Math.max(myTabbedPane.getTabCount() - 2, 0)));
        }
        syncViewToggle();
    }

    public PanelState saveState() {
        PanelState state = new PanelState();
        int plus = plusIndex();
        int selected = myTabbedPane.getSelectedIndex();
        int selectedFileTabIndex = -1;

        for (int i = 0; i < myTabbedPane.getTabCount(); i++) {
            if (i == plus || !(myTabbedPane.getComponentAt(i) instanceof FileTab tab)) {
                continue;
            }
            if (i == selected) {
                selectedFileTabIndex = state.tabs.size();
            }
            state.tabs.add(tab.saveTabState());
        }
        state.selectedTabIndex = selectedFileTabIndex >= 0
            ? selectedFileTabIndex
            : Math.max(state.tabs.size() - 1, 0);
        return state;
    }
}
