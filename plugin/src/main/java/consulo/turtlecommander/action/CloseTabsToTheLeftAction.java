package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnActionEvent;

/** Closes the tabs before the right-clicked one. */
@ActionImpl(id = "TurtleCommander.CloseTabsToTheLeft")
public class CloseTabsToTheLeftAction extends TabContextAction {
    public CloseTabsToTheLeftAction() {
        super(LocalizeValue.localizeTODO("Close Tabs to the Left"));
    }

    @Override
    public void update(AnActionEvent e) {
        TabContext context = resolveTabContext(e);
        e.getPresentation().setEnabled(context != null && context.panel().getRealTabCount() > 1);
    }

    @Override
    protected void performOnTab(TabContext context, AnActionEvent e) {
        context.panel().closeTabsToTheLeft(context.tabIndex());
    }
}
