package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Batch-renames the selection from a template. */
@ActionImpl(id = "TurtleCommander.MultiRename")
public class MultiRenameAction extends FileManagerAction.Simple {
    public MultiRenameAction() {
        super(LocalizeValue.localizeTODO("Multi-Rename..."), "ctrl M", FileTabOperationUtil::performMultiRename);
    }
}
