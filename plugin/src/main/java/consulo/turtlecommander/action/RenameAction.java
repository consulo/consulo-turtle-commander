package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Renames the selected entry. */
@ActionImpl(id = "TurtleCommander.Rename")
public class RenameAction extends FileManagerAction.Simple {
    public RenameAction() {
        super(LocalizeValue.localizeTODO("Rename"), "shift F6", FileTabOperationUtil::startRename);
    }
}
