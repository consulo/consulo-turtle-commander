package consulo.turtlecommander.ui;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.turtlecommander.service.PanelState;
import consulo.turtlecommander.service.TabState;
import consulo.ui.Component;
import consulo.ui.Tab;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.TabbedLayout;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import consulo.util.dataholder.Key;
import consulo.turtlecommander.action.ActionTargets;
import java.util.ArrayDeque;
import java.util.Deque;
import consulo.application.Application;
import consulo.turtlecommander.dialog.FileSearchDialogDescriptor;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.search.FileSearchCriteria;
import consulo.ui.ex.dialog.DialogService;
import consulo.virtualFileSystem.archive.ArchivePath;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionToolbar;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.layout.TabbedLayoutStyle;

/**
 * One side of the file manager: the tabs of a panel, each a {@link UnifiedFileTab}, restored from and saved into the
 * panel's persisted state. The "+" after the tabs opens a new one on the folder of the tab in use; every tab but a lone
 * one can be closed.
 */
public final class UnifiedFilePane implements Disposable, FileManagerTabStrip {
    /** The pane a {@link UnifiedFileTab} belongs to, in the data context of the tab - what the tab menu acts on. */
    public static final Key<UnifiedFilePane> KEY = Key.create(UnifiedFilePane.class);

    /** How many closed tabs can be reopened. */
    private static final int MAX_CLOSED_TABS = 10;

    /** A tab of the strip with what it shows and where it is saved. */
    private record PaneTab(Tab tab, UnifiedFileTab fileTab, TabState state) {
    }

    private static final String NEW_TAB_PLACE = "TurtleCommander.NewTab";
    private static final String TAB_CONTEXT_MENU_GROUP = "TurtleCommander.TabContextMenu";

    private final Project myProject;
    private final PanelState myState;
    private final Path myFallbackPath;
    private final Supplier<@Nullable UnifiedFilePane> myOtherPane;
    private final Consumer<UnifiedFilePane> myOnActivated;

    private final TabbedLayout myTabs = TabbedLayout.create();
    /** In the order of the strip, which is the order of the saved tabs. */
    private final List<PaneTab> myPaneTabs = new ArrayList<>();
    private @Nullable UnifiedFileTab myActiveTab;
    /** The most recently closed last - what Reopen Closed Tab brings back. */
    private final Deque<TabState> myClosedTabs = new ArrayDeque<>();

    /**
     * @param otherPane   the pane on the other side, asked lazily since the two are built one after the other
     * @param onActivated told when a tab of this pane is used - the view makes this the active pane
     */
    @RequiredUIAccess
    public UnifiedFilePane(Project project, PanelState state, Path fallbackPath, Supplier<@Nullable UnifiedFilePane> otherPane,
                           Consumer<UnifiedFilePane> onActivated) {
        myProject = project;
        myState = state;
        myFallbackPath = fallbackPath;
        myOtherPane = otherPane;
        myOnActivated = onActivated;

        // the listings run to the pane's edges; the tab content insets framed them in a margin of their own
        myTabs.addStyle(TabbedLayoutStyle.NO_PADDING);

        if (state.tabs.isEmpty()) {
            state.tabs.add(new TabState(fallbackPath.toString(), ViewMode.TABLE.name()));
        }
        for (TabState tabState : state.tabs) {
            addPaneTab(tabState, myPaneTabs.size());
        }

        myTabs.setSuffixComponent(createNewTabToolbar());
        updateCloseButtons();

        int selected = state.selectedTabIndex;
        if (selected < 0 || selected >= myPaneTabs.size()) {
            selected = 0;
        }
        select(myPaneTabs.get(selected));

        myTabs.addSelectListener(event -> {
            PaneTab paneTab = find(event.getTab());
            if (paneTab != null) {
                // the tab shown is saved with the rest, so a restart lands on it
                myState.selectedTabIndex = myPaneTabs.indexOf(paneTab);
                myActiveTab = paneTab.fileTab();
                myOnActivated.accept(this);
            }
        });
    }

    /** Opens a new tab on {@code directory} after the last one and shows it. */
    @RequiredUIAccess
    public void openTab(Path directory) {
        UnifiedFileTab current = myActiveTab;
        String viewMode = current != null ? current.getViewMode().name() : ViewMode.TABLE.name();
        insertTab(new TabState(directory.toString(), viewMode), myPaneTabs.size());
    }

    @RequiredUIAccess
    private PaneTab insertTab(TabState tabState, int index) {
        myState.tabs.add(index, tabState);
        PaneTab paneTab = addPaneTab(tabState, index);
        select(paneTab);
        return paneTab;
    }

    // --- FileManagerTabStrip -------------------------------------------------------------------------

    @Override
    public int getRealTabCount() {
        return myPaneTabs.size();
    }

    @Override
    public @Nullable FileManagerTab getTabAt(int index) {
        return index >= 0 && index < myPaneTabs.size() ? myPaneTabs.get(index).fileTab() : null;
    }

    @Override
    public int getActiveTabIndex() {
        PaneTab active = findByFileTab(myActiveTab);
        return active != null ? myPaneTabs.indexOf(active) : -1;
    }

    /** The index of {@code fileTab} in this pane, or -1. */
    public int indexOf(UnifiedFileTab fileTab) {
        PaneTab paneTab = findByFileTab(fileTab);
        return paneTab != null ? myPaneTabs.indexOf(paneTab) : -1;
    }

    @RequiredUIAccess
    @Override
    public void closeTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < myPaneTabs.size()) {
            closeTab(myPaneTabs.get(tabIndex));
        }
    }

    @RequiredUIAccess
    @Override
    public void closeOtherTabs(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= myPaneTabs.size()) {
            return;
        }
        PaneTab kept = myPaneTabs.get(tabIndex);
        select(kept);
        for (PaneTab paneTab : List.copyOf(myPaneTabs)) {
            if (paneTab != kept) {
                closeTab(paneTab);
            }
        }
    }

    @RequiredUIAccess
    @Override
    public void closeAllTabs() {
        int active = getActiveTabIndex();
        closeOtherTabs(active >= 0 ? active : 0);
    }

    @RequiredUIAccess
    @Override
    public void closeTabsToTheLeft(int tabIndex) {
        if (tabIndex <= 0 || tabIndex >= myPaneTabs.size()) {
            return;
        }
        for (PaneTab paneTab : List.copyOf(myPaneTabs.subList(0, tabIndex))) {
            closeTab(paneTab);
        }
    }

    @RequiredUIAccess
    @Override
    public void closeTabsToTheRight(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= myPaneTabs.size() - 1) {
            return;
        }
        for (PaneTab paneTab : List.copyOf(myPaneTabs.subList(tabIndex + 1, myPaneTabs.size()))) {
            closeTab(paneTab);
        }
    }

    /** A copy of the tab right after it, on its folder and in its view. */
    @RequiredUIAccess
    @Override
    public void duplicateTab(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= myPaneTabs.size()) {
            return;
        }
        UnifiedFileTab source = myPaneTabs.get(tabIndex).fileTab();
        insertTab(new TabState(source.getCurrentPath().toString(), source.getViewMode().name()), tabIndex + 1);
    }

    @Override
    public boolean hasDuplicateTabs(int tabIndex) {
        return !duplicatesOf(tabIndex).isEmpty();
    }

    @RequiredUIAccess
    @Override
    public void closeDuplicateTabs(int tabIndex) {
        for (PaneTab duplicate : duplicatesOf(tabIndex)) {
            closeTab(duplicate);
        }
    }

    private List<PaneTab> duplicatesOf(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= myPaneTabs.size()) {
            return List.of();
        }
        PaneTab original = myPaneTabs.get(tabIndex);
        Path path = original.fileTab().getCurrentPath();
        List<PaneTab> duplicates = new ArrayList<>();
        for (PaneTab paneTab : myPaneTabs) {
            if (paneTab != original && paneTab.fileTab().getCurrentPath().equals(path)) {
                duplicates.add(paneTab);
            }
        }
        return duplicates;
    }

    @Override
    public boolean hasClosedTabs() {
        return !myClosedTabs.isEmpty();
    }

    @RequiredUIAccess
    @Override
    public void reopenLastClosedTab() {
        TabState closed = myClosedTabs.pollLast();
        if (closed != null) {
            insertTab(closed, myPaneTabs.size());
        }
    }

    @RequiredUIAccess
    @Override
    public void openDirectoryInNewTab(Path path, @Nullable String selectName) {
        UnifiedFileTab current = myActiveTab;
        String viewMode = current != null ? current.getViewMode().name() : ViewMode.TABLE.name();
        PaneTab paneTab = insertTab(new TabState(path.toString(), viewMode), myPaneTabs.size());
        if (selectName != null) {
            paneTab.fileTab().navigateTo(path, selectName);
        }
    }

    /** Shows the next tab, or the first after the last. */
    @RequiredUIAccess
    public void selectNextTab() {
        selectRelative(1);
    }

    /** Shows the previous tab, or the last before the first. */
    @RequiredUIAccess
    public void selectPreviousTab() {
        selectRelative(-1);
    }

    @RequiredUIAccess
    private void selectRelative(int offset) {
        int size = myPaneTabs.size();
        PaneTab current = findByFileTab(myActiveTab);
        int index = current != null ? myPaneTabs.indexOf(current) : 0;
        select(myPaneTabs.get(Math.floorMod(index + offset, size)));
    }

    // --- Search ------------------------------------------------------------------------------------

    /**
     * Opens a tab of search results after the file tabs and starts the search. Like the Swing panel's, a search tab is
     * not saved with the panel - it is a result, not a place.
     */
    @RequiredUIAccess
    public void openSearchTab(FileSearchCriteria criteria) {
        UnifiedSearchResultsView view = new UnifiedSearchResultsView(myProject, criteria, this::revealSearchResult);
        Disposer.register(this, view);

        Tab tab = myTabs.createTab();
        tab.setRenderer((each, presentation) -> presentation.append("Search: " + searchTabLabel(view.getCriteria())));
        tab.setCloseHandler((each, component) -> {
            myTabs.removeTab(tab);
            Disposer.dispose(view);
        });
        view.setOnEditSearch(() -> editSearch(view, tab));
        myTabs.addTab(tab, view.getComponent());
        tab.select();

        view.startSearch();
    }

    @RequiredUIAccess
    private void editSearch(UnifiedSearchResultsView view, Tab tab) {
        FileSearchCriteria criteria = view.getCriteria();
        FileSearchDialogDescriptor dialog = new FileSearchDialogDescriptor(criteria.rootPath(), criteria);
        Application.get().getInstance(DialogService.class).build(dialog).showAsync().whenComplete((value, error) -> {
            if (error == null) {
                view.restart(dialog.getCriteria());
                tab.update();
            }
        });
    }

    /** A folder found opens in a tab of its own; a file opens its folder with the file selected. */
    @RequiredUIAccess
    private void revealSearchResult(FileEntry entry) {
        Path parent = entry.path().getParent();
        if (entry.directory() || parent == null) {
            openDirectoryInNewTab(entry.path(), null);
        }
        else {
            openDirectoryInNewTab(parent, entry.name());
        }
    }

    private static String searchTabLabel(FileSearchCriteria criteria) {
        if (criteria.namePattern() != null) {
            return criteria.namePattern();
        }
        Path fileName = criteria.rootPath().getFileName();
        return fileName != null ? fileName.toString() : "Results";
    }

    /** The tab last used in this pane. */
    public @Nullable UnifiedFileTab getActiveTab() {
        return myActiveTab;
    }

    public Component getComponent() {
        return myTabs;
    }

    /** A toolbar of its one action, so the "+" is sized and highlighted like every other toolbar button. */
    @RequiredUIAccess
    private Component createNewTabToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new DumbAwareAction(LocalizeValue.localizeTODO("New Tab"), LocalizeValue.empty(), PlatformIconGroup.generalAdd()) {
            @RequiredUIAccess
            @Override
            public void actionPerformed(AnActionEvent e) {
                UnifiedFileTab current = myActiveTab;
                openTab(current != null ? current.getCurrentPath() : myFallbackPath);
            }
        });
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(NEW_TAB_PLACE, group, true);
        toolbar.setTargetUIComponent(myTabs);
        return toolbar.getUIComponent();
    }

    /** A lone tab cannot be closed, so it shows no close button at all rather than one that does nothing. */
    @RequiredUIAccess
    private void updateCloseButtons() {
        boolean closable = myPaneTabs.size() > 1;
        for (PaneTab paneTab : myPaneTabs) {
            paneTab.tab().setCloseHandler(closable ? (each, component) -> closeTab(paneTab) : null);
            paneTab.tab().update();
        }
    }

    @RequiredUIAccess
    private PaneTab addPaneTab(TabState tabState, int index) {
        UnifiedFileTab fileTab = new UnifiedFileTab(myProject, tabState, initialPath(tabState, myFallbackPath));
        Disposer.register(this, fileTab);

        Tab tab = myTabs.createTab();
        tab.setRenderer((each, presentation) -> presentation.append(title(fileTab.getCurrentPath())));
        PaneTab paneTab = new PaneTab(tab, fileTab, tabState);
        tab.setPopupGroup(TAB_CONTEXT_MENU_GROUP, ActionTargets.TAB_CONTEXT_PLACE);
        fileTab.setPane(this);
        myTabs.addTab(tab, fileTab.getComponent(), index);

        fileTab.setOnLocationChanged(changed -> tab.update());
        fileTab.setPanelLinks(() -> {
            UnifiedFilePane other = myOtherPane.get();
            return other != null ? other.getActiveTab() : null;
        }, used -> {
            myActiveTab = used;
            myOnActivated.accept(this);
        });

        myPaneTabs.add(index, paneTab);
        updateCloseButtons();
        return paneTab;
    }

    /** A pane keeps at least one tab - closing the last would leave nothing to work in. */
    @RequiredUIAccess
    private void closeTab(PaneTab paneTab) {
        int index = myPaneTabs.indexOf(paneTab);
        if (index < 0 || myPaneTabs.size() == 1) {
            return;
        }
        boolean wasActive = paneTab.fileTab() == myActiveTab;

        myPaneTabs.remove(index);
        myState.tabs.remove(paneTab.state());
        myClosedTabs.addLast(paneTab.state());
        if (myClosedTabs.size() > MAX_CLOSED_TABS) {
            myClosedTabs.removeFirst();
        }
        myTabs.removeTab(paneTab.tab());
        Disposer.dispose(paneTab.fileTab());
        updateCloseButtons();

        if (wasActive) {
            select(myPaneTabs.get(Math.min(index, myPaneTabs.size() - 1)));
        }
        else {
            UnifiedFileTab active = myActiveTab;
            PaneTab activeTab = findByFileTab(active);
            myState.selectedTabIndex = activeTab != null ? myPaneTabs.indexOf(activeTab) : 0;
        }
    }

    @RequiredUIAccess
    private void select(PaneTab paneTab) {
        paneTab.tab().select();
        UnifiedFileTab fileTab = paneTab.fileTab();
        myActiveTab = fileTab;
        myState.selectedTabIndex = myPaneTabs.indexOf(paneTab);
        // the listing of a tab just shown is not on screen yet - focused now, the browser drops the focus on the page
        fileTab.getUIAccess().give(fileTab::focusActiveView);
    }

    private @Nullable PaneTab find(Tab tab) {
        for (PaneTab paneTab : myPaneTabs) {
            if (paneTab.tab() == tab) {
                return paneTab;
            }
        }
        return null;
    }

    private @Nullable PaneTab findByFileTab(@Nullable UnifiedFileTab fileTab) {
        for (PaneTab paneTab : myPaneTabs) {
            if (paneTab.fileTab() == fileTab) {
                return paneTab;
            }
        }
        return null;
    }

    /** A saved path which is gone or unreadable falls back, so a removed drive does not leave an empty tab. */
    private static Path initialPath(TabState state, Path fallbackPath) {
        if (state.path.isEmpty()) {
            return fallbackPath;
        }
        try {
            Path path = Path.of(state.path);
            return Files.isDirectory(path) ? path : fallbackPath;
        }
        catch (InvalidPathException e) {
            return fallbackPath;
        }
    }

    private static String title(Path path) {
        // inside an archive the tab is named like a folder, and the archive's root by the archive
        if (path instanceof ArchivePath archivePath && archivePath.getEntryPath().isEmpty()) {
            path = archivePath.getArchiveFile();
        }
        Path fileName = path.getFileName();
        return fileName != null ? fileName.toString() : path.toString();
    }

    @Override
    public void dispose() {
    }
}
