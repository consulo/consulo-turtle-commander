package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnActionEvent;

/** Opens another tab on the same directory. */
@ActionImpl(id = "TurtleCommander.DuplicateTab")
public class DuplicateTabAction extends TabContextAction {
    public DuplicateTabAction() {
        super(LocalizeValue.localizeTODO("Duplicate Tab"));
    }

    @Override
    protected void performOnTab(TabContext context, AnActionEvent e) {
        context.panel().duplicateTab(context.tabIndex());
    }
}
