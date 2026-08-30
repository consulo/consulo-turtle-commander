package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnActionEvent;

/** Closes the tabs after the right-clicked one. */
@ActionImpl(id = "TurtleCommander.CloseTabsToTheRight")
public class CloseTabsToTheRightAction extends TabContextAction {
    public CloseTabsToTheRightAction() {
        super(LocalizeValue.localizeTODO("Close Tabs to the Right"));
    }

    @Override
    public void update(AnActionEvent e) {
        TabContext context = resolveTabContext(e);
        e.getPresentation().setEnabled(context != null && context.panel().getRealTabCount() > 1);
    }

    @Override
    protected void performOnTab(TabContext context, AnActionEvent e) {
        context.panel().closeTabsToTheRight(context.tabIndex());
    }
}
