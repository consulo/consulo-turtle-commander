package consulo.turtlecommander.action;

import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.editor.TctFileEditor;
import consulo.turtlecommander.ui.FileManagerTab;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.TurtleCommanderToolWindowFactory;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.toolWindow.ToolWindow;
import org.jspecify.annotations.Nullable;
import consulo.turtlecommander.ui.FileManagerTabStrip;
import consulo.turtlecommander.ui.UnifiedFileTab;

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

    /** The active tab of the open file manager, or null when none is showing. */
    public static @Nullable FileManagerTab findActiveTab(AnActionEvent e) {
        Project project = e.getData(Project.KEY);

        if (project == null) {
            return null;
        }
        FileManagerStateService stateService = FileManagerStateService.getInstance(project);
        FileManagerTab tab = stateService.getActiveFileManagerTab();
        if (tab != null && !(tab instanceof FileTab)) {
            // the unified view lives in an editor, not the tool window: its tab is the target only while that editor is
            // shown. Its keys are bound to the view itself, so they come from inside it; the browser frontend does not
            // put the editor into the data context of a key bound to a component, so the editor manager is asked
            return isTurtleEditorShown(e, project) ? tab : null;
        }
        ToolWindow toolWindow = toolWindow(project);
        if (toolWindow == null || !toolWindow.isVisible()) {
            return null;
        }
        return stateService.getActiveTab();
    }

    private static boolean isTurtleEditorShown(AnActionEvent e, Project project) {
        if (e.getData(FileEditor.KEY) instanceof TctFileEditor) {
            return true;
        }
        for (FileEditor editor : FileEditorManager.getInstance(project).getSelectedEditors()) {
            if (editor instanceof TctFileEditor) {
                return true;
            }
        }
        return false;
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
    public static @Nullable FileManagerTab resolveFileContextTab(AnActionEvent e) {
        if (FILE_CONTEXT_PLACE.equals(e.getPlace())) {
            FileManagerTab clicked = FileContextMenuState.getClickedTab(e);
            return clicked != null ? clicked : findActiveTab(e);
        }
        FileManagerTab tab = findActiveTab(e);
        return tab != null && tab.hasAnyViewFocus() ? tab : null;
    }

    /**
     * The tabs the tab actions work on from the keyboard: the pane of the unified view while the Turtle editor is where
     * the input goes, the active panel of the tool window while that has focus.
     */
    public static @Nullable FileManagerTabStrip findActiveTabStrip(AnActionEvent e) {
        FileManagerStateService stateService = stateService(e);
        if (stateService == null) {
            return null;
        }
        if (findActiveTab(e) instanceof UnifiedFileTab) {
            return stateService.getUnifiedActivePane();
        }
        return isToolWindowActive(e) ? stateService.getActivePanel() : null;
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
