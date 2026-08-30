package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Creates a file and opens it in the editor. */
@ActionImpl(id = "TurtleCommander.CreateFile")
public class CreateFileAction extends FileManagerAction.Simple {
    public CreateFileAction() {
        super(LocalizeValue.localizeTODO("Create File"), "shift F4", FileTabOperationUtil::performCreateFile);
    }
}
