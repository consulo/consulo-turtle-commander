package consulo.turtlecommander.action;

import consulo.turtlecommander.ui.FileManagerTab;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;

/** Opens the right-clicked file. */
@ActionImpl(id = "TurtleCommander.OpenFile")
public class OpenFileAction extends FileContextAction.Simple {
    public OpenFileAction() {
        super(LocalizeValue.localizeTODO("Open"), null, FileContextAction::isFile, true, FileManagerTab::openSelectedEntry);
    }
}
