package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.ui.ex.action.AnActionEvent;

/** Opens another tab on the active panel's directory. */
@ActionImpl(id = "TurtleCommander.NewTab")
public class NewTabAction extends FileManagerAction.ToolWindowScoped {
    public NewTabAction() {
        super(LocalizeValue.localizeTODO("New Tab"), "ctrl T");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileManagerStateService stateService = ActionTargets.stateService(e);
        if (stateService == null) {
            return;
        }
        FileManagerPanel panel = stateService.getActivePanel();
        if (panel != null) {
            panel.openNewTab();
        }
    }
}
