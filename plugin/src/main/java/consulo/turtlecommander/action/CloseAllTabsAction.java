package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnActionEvent;

/** Closes every tab but the first, since a panel always keeps one. */
@ActionImpl(id = "TurtleCommander.CloseAllTabs")
public class CloseAllTabsAction extends TabContextAction {
    public CloseAllTabsAction() {
        super(LocalizeValue.localizeTODO("Close All Tabs"));
    }

    @Override
    public void update(AnActionEvent e) {
        TabContext context = resolveTabContext(e);
        e.getPresentation().setEnabled(context != null && context.panel().getRealTabCount() > 1);
    }

    @Override
    protected void performOnTab(TabContext context, AnActionEvent e) {
        context.panel().closeAllTabs();
    }
}
