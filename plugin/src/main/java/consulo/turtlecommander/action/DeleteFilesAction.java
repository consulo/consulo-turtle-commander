package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabOperationUtil;
import consulo.ui.ex.action.AnActionEvent;

import java.awt.event.InputEvent;

/** Deletes the selection, honouring the recycle-bin setting unless Shift forces a permanent delete. */
@ActionImpl(id = "TurtleCommander.DeleteFiles")
public class DeleteFilesAction extends FileManagerAction {
    public DeleteFilesAction() {
        super(LocalizeValue.localizeTODO("Delete Files"), "F8");
    }

    @Override
    protected void performOnTab(FileTab tab, AnActionEvent e) {
        // Shift+Delete (and Shift+F8) always deletes irreversibly, bypassing the setting. The input
        // event only exists for keyboard and mouse triggers — a menu invocation follows the setting.
        InputEvent inputEvent = e.getInputEvent();
        FileTabOperationUtil.performDelete(tab, inputEvent != null && inputEvent.isShiftDown());
    }
}
