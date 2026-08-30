package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabSelectionUtil;

/** Marks or unmarks the entry under the cursor. */
@ActionImpl(id = "TurtleCommander.ToggleSelection")
public class ToggleSelectionAction extends FileManagerAction.Simple {
    public ToggleSelectionAction() {
        super(LocalizeValue.localizeTODO("Toggle Selection"), "SPACE", FileTabSelectionUtil::toggleSelection);
    }
}
