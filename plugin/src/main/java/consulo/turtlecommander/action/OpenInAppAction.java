package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Hands the selected file to the OS file association. */
@ActionImpl(id = "TurtleCommander.OpenInApp")
public class OpenInAppAction extends FileManagerAction.Simple {
    public OpenInAppAction() {
        super(LocalizeValue.localizeTODO("Open in Associated Application"), "F4", FileTabOperationUtil::openSelectedInAssociatedApp);
    }
}
