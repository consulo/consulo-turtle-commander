package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabNavigationUtil;

/** Opens the selected file or enters the selected directory. */
@ActionImpl(id = "TurtleCommander.OpenEntry")
public class OpenEntryAction extends FileManagerAction.Simple {
    public OpenEntryAction() {
        super(LocalizeValue.localizeTODO("Open Entry"), "ENTER", FileTabNavigationUtil::openSelectedEntry);
    }
}
