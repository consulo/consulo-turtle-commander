package consulo.turtlecommander.action;

import consulo.turtlecommander.ui.FileManagerTab;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;

/** Marks or unmarks the entry under the cursor. */
@ActionImpl(id = "TurtleCommander.ToggleSelection")
public class ToggleSelectionAction extends FileManagerAction.Simple {
    public ToggleSelectionAction() {
        super(LocalizeValue.localizeTODO("Toggle Selection"), "SPACE", FileManagerTab::toggleSelection);
    }
}
