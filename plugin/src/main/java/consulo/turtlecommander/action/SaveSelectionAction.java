package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabSelectionUtil;

/** Snapshots the current marks. */
@ActionImpl(id = "TurtleCommander.SaveSelection")
public class SaveSelectionAction extends FileManagerAction.Simple {
    public SaveSelectionAction() {
        super(LocalizeValue.localizeTODO("Save Selection"), "ctrl MULTIPLY", FileTabSelectionUtil::saveCurrentSelection);
    }
}
