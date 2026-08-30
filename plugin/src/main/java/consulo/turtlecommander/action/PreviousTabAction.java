package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.ui.ex.action.AnActionEvent;

/** Selects the previous tab, wrapping at the start. */
@ActionImpl(id = "TurtleCommander.PreviousTab")
public class PreviousTabAction extends FileManagerAction.ToolWindowScoped {
    public PreviousTabAction() {
        super(LocalizeValue.localizeTODO("Previous Tab"), "alt LEFT");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileManagerStateService stateService = ActionTargets.stateService(e);
        if (stateService == null) {
            return;
        }
        FileManagerPanel panel = stateService.getActivePanel();
        if (panel != null) {
            panel.selectPreviousTab();
        }
    }
}
