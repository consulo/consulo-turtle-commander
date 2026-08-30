package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabSelectionUtil;

/** Marks the entry under the cursor and steps down. */
@ActionImpl(id = "TurtleCommander.ToggleSelectionMark")
public class ToggleSelectionMarkAction extends FileManagerAction.Simple {
    public ToggleSelectionMarkAction() {
        super(LocalizeValue.localizeTODO("Toggle Selection (Move Cursor Down)"), "INSERT", FileTabSelectionUtil::toggleSelectionAndMoveDown);
    }
}
