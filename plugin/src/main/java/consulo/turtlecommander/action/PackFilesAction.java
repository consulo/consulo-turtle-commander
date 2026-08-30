package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Packs the selection into an archive. */
@ActionImpl(id = "TurtleCommander.PackFiles")
public class PackFilesAction extends FileContextAction.Simple {
    public PackFilesAction() {
        super(LocalizeValue.localizeTODO("Pack Files"), "shift F1", FileContextAction::hasSelection, false, FileTabOperationUtil::performPack);
    }
}
