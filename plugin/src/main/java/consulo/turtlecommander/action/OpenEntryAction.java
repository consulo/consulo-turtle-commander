package consulo.turtlecommander.action;

import consulo.turtlecommander.ui.FileManagerTab;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;

/** Opens the selected file or enters the selected directory. */
@ActionImpl(id = "TurtleCommander.OpenEntry")
public class OpenEntryAction extends FileManagerAction.Simple {
    public OpenEntryAction() {
        super(LocalizeValue.localizeTODO("Open Entry"), "ENTER", FileManagerTab::openSelectedEntry);
    }
}
