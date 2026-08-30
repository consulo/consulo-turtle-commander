package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabOperationUtil;
import consulo.ui.ex.action.AnActionEvent;

/** Reverses the last multi-rename batch. */
@ActionImpl(id = "TurtleCommander.MultiRenameUndo")
public class MultiRenameUndoAction extends FileManagerAction {
    public MultiRenameUndoAction() {
        super(LocalizeValue.localizeTODO("Undo Multi-Rename"), "ctrl shift M");
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        if (e.getPresentation().isEnabled()) {
            e.getPresentation().setEnabled(FileTabOperationUtil.hasMultiRenameUndo());
        }
    }

    @Override
    protected void performOnTab(FileTab tab, AnActionEvent e) {
        FileTabOperationUtil.performMultiRenameUndo(tab);
    }
}
