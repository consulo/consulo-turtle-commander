package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.ui.ex.action.AnActionEvent;

/** Reopens the most recently closed tab, back in its original position. */
@ActionImpl(id = "TurtleCommander.ReopenTab")
public class ReopenTabAction extends FileManagerAction.ToolWindowScoped {
    public ReopenTabAction() {
        super(LocalizeValue.localizeTODO("Reopen Closed Tab"), "ctrl shift T");
    }

    @Override
    public void update(AnActionEvent e) {
        FileManagerPanel panel = activePanel(e);
        e.getPresentation().setEnabled(ActionTargets.isToolWindowActive(e)
            && panel != null && panel.hasClosedTabs());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileManagerPanel panel = activePanel(e);
        if (panel != null) {
            panel.reopenLastClosedTab();
        }
    }

    private static FileManagerPanel activePanel(AnActionEvent e) {
        FileManagerStateService stateService = ActionTargets.stateService(e);
        return stateService != null ? stateService.getActivePanel() : null;
    }
}
