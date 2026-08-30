package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabNavigationUtil;
import consulo.ui.ex.action.AnActionEvent;

/** Re-lists the right-clicked tab's directory. */
@ActionImpl(id = "TurtleCommander.TabRefresh")
public class TabRefreshAction extends TabContextAction {
    public TabRefreshAction() {
        super(LocalizeValue.localizeTODO("Refresh"));
    }

    @Override
    protected void performOnTab(TabContext context, AnActionEvent e) {
        FileTab tab = fileTabOf(context);
        if (tab != null) {
            FileTabNavigationUtil.refresh(tab, true);
        }
    }
}
