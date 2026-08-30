package consulo.turtlecommander.action;

import consulo.localize.LocalizeValue;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.turtlecommander.ui.FileTab;
import consulo.ui.ex.action.AnActionEvent;
import org.jspecify.annotations.Nullable;

/** An action on the tab strip's context menu, targeting the right-clicked tab. */
public abstract class TabContextAction extends EdtAction {
    /** The panel and tab index an action should act on. */
    public record TabContext(FileManagerPanel panel, int tabIndex) {
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

    /** The right-clicked tab's {@link FileTab}, or null when it isn't a file tab. */
    protected static @Nullable FileTab fileTabOf(TabContext context) {
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
        FileManagerPanel clickedPanel = TabContextMenuState.getClickedPanel(e);
        int clickedIndex = TabContextMenuState.getClickedTabIndex(e);
        if (clickedPanel != null && clickedIndex >= 0) {
            return new TabContext(clickedPanel, clickedIndex);
        }
        if (!ActionTargets.isToolWindowActive(e)) {
            return null;
        }
        FileManagerStateService stateService = ActionTargets.stateService(e);
        FileManagerPanel panel = stateService != null ? stateService.getActivePanel() : null;
        if (panel == null) {
            return null;
        }
        int index = panel.getActiveTabIndex();
        return index >= 0 ? new TabContext(panel, index) : null;
    }
}
