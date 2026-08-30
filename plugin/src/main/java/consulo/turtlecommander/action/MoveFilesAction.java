package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Moves the selection to the other panel. */
@ActionImpl(id = "TurtleCommander.MoveFiles")
public class MoveFilesAction extends FileManagerAction.Simple {
    public MoveFilesAction() {
        super(LocalizeValue.localizeTODO("Move Files"), "F6", FileTabOperationUtil::performMove);
    }
}
