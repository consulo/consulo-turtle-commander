package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Links the selection into the other panel. */
@ActionImpl(id = "TurtleCommander.CreateLink")
public class CreateLinkAction extends FileManagerAction.Simple {
    public CreateLinkAction() {
        super(LocalizeValue.localizeTODO("Create Link"), "alt F6", FileTabOperationUtil::performCreateLink);
    }
}
