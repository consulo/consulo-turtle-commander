package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnActionEvent;

/** Closes every tab but the right-clicked one. */
@ActionImpl(id = "TurtleCommander.CloseOtherTabs")
public class CloseOtherTabsAction extends TabContextAction {
    public CloseOtherTabsAction() {
        super(LocalizeValue.localizeTODO("Close Other Tabs"));
    }

    @Override
    public void update(AnActionEvent e) {
        TabContext context = resolveTabContext(e);
        e.getPresentation().setEnabled(context != null && context.panel().getRealTabCount() > 1);
    }

    @Override
    protected void performOnTab(TabContext context, AnActionEvent e) {
        context.panel().closeOtherTabs(context.tabIndex());
    }
}
