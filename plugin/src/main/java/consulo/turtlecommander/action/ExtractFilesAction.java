package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Extracts the selected archives to a chosen directory. */
@ActionImpl(id = "TurtleCommander.ExtractFiles")
public class ExtractFilesAction extends FileContextAction.Simple {
    public ExtractFilesAction() {
        super(LocalizeValue.localizeTODO("Extract Files..."), "shift F2", FileContextAction::isArchive, true, FileTabOperationUtil::performExtract);
    }
}
