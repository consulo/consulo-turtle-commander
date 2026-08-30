package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.ui.ex.action.AnActionEvent;

/** Selects the next tab, wrapping at the end. */
@ActionImpl(id = "TurtleCommander.NextTab")
public class NextTabAction extends FileManagerAction.ToolWindowScoped {
    public NextTabAction() {
        super(LocalizeValue.localizeTODO("Next Tab"), "alt RIGHT");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileManagerStateService stateService = ActionTargets.stateService(e);
        if (stateService == null) {
            return;
        }
        FileManagerPanel panel = stateService.getActivePanel();
        if (panel != null) {
            panel.selectNextTab();
        }
    }
}
