package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabSelectionUtil;

/** Unmarks every file sharing the cursor entry's extension. */
@ActionImpl(id = "TurtleCommander.UnselectSameExtension")
public class UnselectSameExtensionAction extends FileManagerAction.Simple {
    public UnselectSameExtensionAction() {
        super(LocalizeValue.localizeTODO("Unselect Same Extension"), "alt SUBTRACT", FileTabSelectionUtil::unselectSameExtension);
    }
}
