package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Extracts into a subdirectory named after the archive. */
@ActionImpl(id = "TurtleCommander.ExtractToSubdir")
public class ExtractToSubdirAction extends FileContextAction.Simple {
    public ExtractToSubdirAction() {
        super(LocalizeValue.localizeTODO("Extract to Subdir..."), null, FileContextAction::isArchive, true, FileTabOperationUtil::performExtractToSubdir);
    }
}
