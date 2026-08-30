package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Hands the file to the OS file association. */
@ActionImpl(id = "TurtleCommander.OpenInAssociatedApp")
public class OpenInAssociatedAppAction extends FileContextAction.Simple {
    public OpenInAssociatedAppAction() {
        super(LocalizeValue.localizeTODO("Open in Associated Application"), null, FileContextAction::isFile, true, FileTabOperationUtil::openSelectedInAssociatedApp);
    }
}
