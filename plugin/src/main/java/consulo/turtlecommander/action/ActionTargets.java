package consulo.turtlecommander.action;

import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.TurtleCommanderToolWindowFactory;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.toolWindow.ToolWindow;
import org.jspecify.annotations.Nullable;

/** Resolving which tab or panel an action should act on. */
public final class ActionTargets {
    /** The {@code place} the file context menu is shown with. */
    public static final String FILE_CONTEXT_PLACE = "TurtleCommander.FileContextMenu";

    /** The {@code place} the search-results context menu is shown with. */
    public static final String SEARCH_CONTEXT_PLACE = "TurtleCommander.SearchContextMenu";

    /** The {@code place} the tab-strip context menu is shown with. */
    public static final String TAB_CONTEXT_PLACE = "TurtleCommander.TabContextMenu";

    private ActionTargets() {
    }

    /** The active tab, or null when the tool window isn't showing one. */
    public static @Nullable FileTab findActiveTab(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return null;
        }
        ToolWindow toolWindow = toolWindow(project);
        if (toolWindow == null || !toolWindow.isVisible()) {
            return null;
        }
        return FileManagerStateService.getInstance(project).getActiveTab();
    }

    /**
     * Whether the tool window actually holds focus, not merely that it is visible.
     *
     * <p>These shortcuts live in the global keymap, so gating on visibility alone would make them
     * contend with the IDE's own bindings — Recent Files, editor navigation, bookmarks — whenever
     * the panel was docked open but the editor had focus.
     */
    public static boolean isToolWindowActive(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return false;
        }
        ToolWindow toolWindow = toolWindow(project);
        return toolWindow != null && toolWindow.isActive();
    }

    /**
     * The tab a file context action should target.
     *
     * <ul>
     *   <li>From the file context menu: the right-clicked tab.
     *   <li>From the keyboard or Find Action: only a focused tab. The cached state is stale between
     *       menus and would otherwise let Ctrl+C/X/V hijack the editor's clipboard — or act on a tab
     *       in the other panel — while the tool window was merely open.
     * </ul>
     */
    public static @Nullable FileTab resolveFileContextTab(AnActionEvent e) {
        if (FILE_CONTEXT_PLACE.equals(e.getPlace())) {
            FileTab clicked = FileContextMenuState.getClickedTab(e);
            return clicked != null ? clicked : findActiveTab(e);
        }
        FileTab tab = findActiveTab(e);
        return tab != null && tab.hasAnyViewFocus() ? tab : null;
    }

    public static @Nullable FileManagerStateService stateService(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        return project != null ? FileManagerStateService.getInstance(project) : null;
    }

    public static @Nullable ToolWindow toolWindow(Project project) {
        return ToolWindowManager.getInstance(project)
            .getToolWindow(TurtleCommanderToolWindowFactory.ID);
    }
}
