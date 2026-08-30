package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.ui.ex.action.AnActionEvent;

/** Drops open the right panel's drive selector. */
@ActionImpl(id = "TurtleCommander.RightDriveSelector")
public class RightDriveSelectorAction extends FileManagerAction.ToolWindowScoped {
    public RightDriveSelectorAction() {
        super(LocalizeValue.localizeTODO("Right Panel Drive Selector"), "alt F2");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileManagerStateService stateService = ActionTargets.stateService(e);
        FileManagerPanel panel = stateService != null ? stateService.getRightPanel() : null;
        if (panel != null) {
            panel.showDriveSelector();
        }
    }
}
