package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Renames the right-clicked entry. */
@ActionImpl(id = "TurtleCommander.ContextRename")
public class ContextRenameAction extends FileContextAction.Simple {
    public ContextRenameAction() {
        super(LocalizeValue.localizeTODO("Rename"), null, FileContextAction::isRealEntry, false, FileTabOperationUtil::startRename);
    }
}
