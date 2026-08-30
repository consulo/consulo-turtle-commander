package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.ui.ex.action.AnActionEvent;

/** Shows the bottom command line, targeting the active panel. */
@ActionImpl(id = "TurtleCommander.ExecuteCommand")
public class ExecuteCommandAction extends FileManagerAction.ToolWindowScoped {
    public ExecuteCommandAction() {
        super(LocalizeValue.localizeTODO("Execute Command"), "ctrl E");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileManagerStateService stateService = ActionTargets.stateService(e);
        if (stateService == null) {
            return;
        }
        stateService.activateCommandInput();
    }
}
