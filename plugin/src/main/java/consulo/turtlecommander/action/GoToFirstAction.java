package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabSelectionUtil;
import consulo.ui.ex.action.AnActionEvent;

/** Moves the cursor to the first row. */
@ActionImpl(id = "TurtleCommander.GoToFirst")
public class GoToFirstAction extends FileManagerAction {
    public GoToFirstAction() {
        super(LocalizeValue.localizeTODO("Go to First"), "HOME");
    }

    @Override
    protected void performOnTab(FileTab tab, AnActionEvent e) {
        FileTabSelectionUtil.selectFirstRow(tab);
    }
}
