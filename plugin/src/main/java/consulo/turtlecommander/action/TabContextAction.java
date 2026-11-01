package consulo.turtlecommander.action;

import consulo.localize.LocalizeValue;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.turtlecommander.ui.FileManagerTab;
import consulo.ui.ex.action.AnActionEvent;
import org.jspecify.annotations.Nullable;
import consulo.turtlecommander.ui.FileManagerTabStrip;
import consulo.turtlecommander.ui.UnifiedFilePane;
import consulo.turtlecommander.ui.UnifiedFileTab;

/** An action on the tab strip's context menu, targeting the right-clicked tab. */
public abstract class TabContextAction extends EdtAction {
    /** The panel and tab index an action should act on. */
    public record TabContext(FileManagerTabStrip panel, int tabIndex) {
    }

    protected TabContextAction(LocalizeValue text) {
        super(text);
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(resolveTabContext(e) != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        TabContext context = resolveTabContext(e);
        if (context != null) {
            performOnTab(context, e);
        }
    }

    protected abstract void performOnTab(TabContext context, AnActionEvent e);

    /** The right-clicked tab's {@link FileManagerTab}, or null when it isn't a file tab. */
    protected static @Nullable FileManagerTab fileTabOf(TabContext context) {
        return context.panel().getTabAt(context.tabIndex());
    }

    /**
     * Resolves the target tab.
     *
     * <p>{@link TabContextMenuState} hands back the click only for a dispatch from the tab context
     * menu. From a keyboard shortcut or Find Action it would be stale and could target a tab in the
     * wrong panel — or a different project — so those fall back to the active panel's current tab,
     * and only while the tool window has focus.
     */
    protected static @Nullable TabContext resolveTabContext(AnActionEvent e) {
        if (ActionTargets.TAB_CONTEXT_PLACE.equals(e.getPlace())) {
            // a unified tab hands itself and its pane to the menu shown on it
            UnifiedFileTab clickedTab = e.getData(UnifiedFileTab.KEY);
            UnifiedFilePane clickedPane = e.getData(UnifiedFilePane.KEY);
            if (clickedTab != null && clickedPane != null) {
                int index = clickedPane.indexOf(clickedTab);
                return index >= 0 ? new TabContext(clickedPane, index) : null;
            }
        }
        FileManagerPanel clickedPanel = TabContextMenuState.getClickedPanel(e);
        int clickedIndex = TabContextMenuState.getClickedTabIndex(e);
        if (clickedPanel != null && clickedIndex >= 0) {
            return new TabContext(clickedPanel, clickedIndex);
        }
        FileManagerTabStrip strip = ActionTargets.findActiveTabStrip(e);
        if (strip == null) {
            return null;
        }
        int index = strip.getActiveTabIndex();
        return index >= 0 ? new TabContext(strip, index) : null;
    }
}
