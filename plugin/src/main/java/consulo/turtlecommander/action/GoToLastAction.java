package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileManagerTab;
import consulo.ui.ex.action.AnActionEvent;

/** Moves the cursor to the last row. */
@ActionImpl(id = "TurtleCommander.GoToLast")
public class GoToLastAction extends FileManagerAction {
    public GoToLastAction() {
        super(LocalizeValue.localizeTODO("Go to Last"), "END");
    }

    @Override
    protected void performOnTab(FileManagerTab tab, AnActionEvent e) {
        tab.selectLastRow();
    }
}
