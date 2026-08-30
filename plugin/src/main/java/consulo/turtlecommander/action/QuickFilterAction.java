package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTab;
import consulo.ui.ex.action.AnActionEvent;

/** Shows the quick-filter bar. */
@ActionImpl(id = "TurtleCommander.QuickFilter")
public class QuickFilterAction extends FileManagerAction {
    public QuickFilterAction() {
        super(LocalizeValue.localizeTODO("Quick Filter"), "ctrl S");
    }

    @Override
    public void update(AnActionEvent e) {
        // Reachable from anywhere in the tool window, not only from a focused view.
        e.getPresentation().setEnabled(ActionTargets.isToolWindowActive(e));
    }

    @Override
    protected void performOnTab(FileTab tab, AnActionEvent e) {
        tab.showQuickFilter();
    }
}
