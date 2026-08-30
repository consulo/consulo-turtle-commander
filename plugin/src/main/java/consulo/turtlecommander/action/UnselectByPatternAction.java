package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabSelectionUtil;
import consulo.ui.ex.action.AnActionEvent;

/** Unmarks every displayed file matching a glob mask. */
@ActionImpl(id = "TurtleCommander.UnselectByPattern")
public class UnselectByPatternAction extends FileManagerAction {
    public UnselectByPatternAction() {
        super(LocalizeValue.localizeTODO("Unselect by Pattern..."), "SUBTRACT");
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        if (e.getPresentation().isEnabled()) {
            FileTab tab = ActionTargets.findActiveTab(e);
            // Nothing marked means nothing to unmark.
            e.getPresentation().setEnabled(tab != null && !tab.getMarkedPaths().isEmpty());
        }
    }

    @Override
    protected void performOnTab(FileTab tab, AnActionEvent e) {
        String mask = SelectByPatternAction.askForMask(e.getData(Project.KEY), "Unselect Files");
        if (mask != null) {
            FileTabSelectionUtil.unselectByMask(tab, mask, false);
        }
    }
}
