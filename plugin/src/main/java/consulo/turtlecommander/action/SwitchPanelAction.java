package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.ui.ex.action.AnActionEvent;

/** Moves focus to the opposite panel. */
@ActionImpl(id = "TurtleCommander.SwitchPanel")
public class SwitchPanelAction extends FileManagerAction.ToolWindowScoped {
    public SwitchPanelAction() {
        super(LocalizeValue.localizeTODO("Switch Panel"), "TAB");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileManagerStateService stateService = ActionTargets.stateService(e);
        if (stateService == null) {
            return;
        }
        stateService.switchToOtherPanel();
    }
}
