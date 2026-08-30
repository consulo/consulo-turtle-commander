package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Deletes the selection. */
@ActionImpl(id = "TurtleCommander.ContextDelete")
public class ContextDeleteAction extends FileContextAction.Simple {
    public ContextDeleteAction() {
        super(LocalizeValue.localizeTODO("Delete"), null, FileContextAction::hasSelection, false, tab -> FileTabOperationUtil.performDelete(tab, false));
    }
}
