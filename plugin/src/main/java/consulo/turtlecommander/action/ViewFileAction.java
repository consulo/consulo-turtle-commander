package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Opens the selected file in the editor. */
@ActionImpl(id = "TurtleCommander.ViewFile")
public class ViewFileAction extends FileManagerAction.Simple {
    public ViewFileAction() {
        super(LocalizeValue.localizeTODO("View File"), "F3", FileTabOperationUtil::viewSelectedFile);
    }
}
