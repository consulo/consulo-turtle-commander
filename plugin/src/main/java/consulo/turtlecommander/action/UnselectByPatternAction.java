package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileManagerTab;
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
            FileManagerTab tab = ActionTargets.findActiveTab(e);
            // Nothing marked means nothing to unmark.
            e.getPresentation().setEnabled(tab != null && !tab.getMarkedPaths().isEmpty());
        }
    }

    @Override
    protected void performOnTab(FileManagerTab tab, AnActionEvent e) {
        SelectByPatternAction.askForMask("Unselect Files", mask -> tab.markByMask(mask, false, false));
    }
}
