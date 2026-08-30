package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabNavigationUtil;
import consulo.ui.ex.action.AnActionEvent;

/** Re-lists the current directory. */
@ActionImpl(id = "TurtleCommander.Refresh")
public class RefreshAction extends FileManagerAction {
    public RefreshAction() {
        super(LocalizeValue.localizeTODO("Refresh"), "ctrl R");
    }

    @Override
    protected void performOnTab(FileTab tab, AnActionEvent e) {
        FileTabNavigationUtil.refresh(tab, true);
    }
}
