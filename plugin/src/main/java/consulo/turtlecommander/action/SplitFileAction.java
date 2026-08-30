package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Splits the file into fixed-size chunks. */
@ActionImpl(id = "TurtleCommander.SplitFile")
public class SplitFileAction extends FileContextAction.Simple {
    public SplitFileAction() {
        super(LocalizeValue.localizeTODO("Split File..."), null, FileContextAction::isFileOutsideArchive, true, FileTabOperationUtil::performSplitFile);
    }
}
