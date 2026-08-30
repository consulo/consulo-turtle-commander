package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Creates a directory in the current one. */
@ActionImpl(id = "TurtleCommander.CreateDirectory")
public class CreateDirectoryAction extends FileManagerAction.Simple {
    public CreateDirectoryAction() {
        super(LocalizeValue.localizeTODO("Create Directory"), "F7", FileTabOperationUtil::performCreateDirectory);
    }
}
