package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnActionEvent;

/** Closes other tabs showing the same directory. */
@ActionImpl(id = "TurtleCommander.CloseDuplicateTabs")
public class CloseDuplicateTabsAction extends TabContextAction {
    public CloseDuplicateTabsAction() {
        super(LocalizeValue.localizeTODO("Close Duplicate Tabs"));
    }

    @Override
    public void update(AnActionEvent e) {
        TabContext context = resolveTabContext(e);
        e.getPresentation().setEnabled(context != null
            && context.panel().hasDuplicateTabs(context.tabIndex()));
    }

    @Override
    protected void performOnTab(TabContext context, AnActionEvent e) {
        context.panel().closeDuplicateTabs(context.tabIndex());
    }
}
