package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Copies the selection to the other panel. */
@ActionImpl(id = "TurtleCommander.CopyFiles")
public class CopyFilesAction extends FileManagerAction.Simple {
    public CopyFilesAction() {
        super(LocalizeValue.localizeTODO("Copy Files"), "F5", FileTabOperationUtil::performCopy);
    }
}
