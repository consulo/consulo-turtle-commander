package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Batch-renames the selection. */
@ActionImpl(id = "TurtleCommander.ContextMultiRename")
public class ContextMultiRenameAction extends FileContextAction.Simple {
    public ContextMultiRenameAction() {
        super(LocalizeValue.localizeTODO("Multi-Rename..."), null, FileContextAction::hasSelection, false, FileTabOperationUtil::performMultiRename);
    }
}
