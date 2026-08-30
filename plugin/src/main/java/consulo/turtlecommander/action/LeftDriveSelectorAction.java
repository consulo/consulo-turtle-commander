package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.ui.ex.action.AnActionEvent;

/** Drops open the left panel's drive selector. */
@ActionImpl(id = "TurtleCommander.LeftDriveSelector")
public class LeftDriveSelectorAction extends FileManagerAction.ToolWindowScoped {
    public LeftDriveSelectorAction() {
        super(LocalizeValue.localizeTODO("Left Panel Drive Selector"), "alt F1");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileManagerStateService stateService = ActionTargets.stateService(e);
        FileManagerPanel panel = stateService != null ? stateService.getLeftPanel() : null;
        if (panel != null) {
            panel.showDriveSelector();
        }
    }
}
