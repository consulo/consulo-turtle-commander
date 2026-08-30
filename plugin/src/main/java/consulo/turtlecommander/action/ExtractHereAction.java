package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Extracts the selected archives into the current directory. */
@ActionImpl(id = "TurtleCommander.ExtractHere")
public class ExtractHereAction extends FileContextAction.Simple {
    public ExtractHereAction() {
        super(LocalizeValue.localizeTODO("Extract Here"), null, FileContextAction::isArchive, true, FileTabOperationUtil::performExtractHere);
    }
}
