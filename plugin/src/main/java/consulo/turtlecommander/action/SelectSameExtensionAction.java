package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;

/** Marks every file sharing the cursor entry's extension. */
@ActionImpl(id = "TurtleCommander.SelectSameExtension")
public class SelectSameExtensionAction extends FileManagerAction.Simple {
    public SelectSameExtensionAction() {
        super(LocalizeValue.localizeTODO("Select Same Extension"), "alt ADD", tab -> tab.markSameExtension(true));
    }
}
