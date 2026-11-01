package consulo.turtlecommander.action;

import consulo.turtlecommander.ui.FileManagerTab;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;

/** Marks the entry under the cursor and steps down. */
@ActionImpl(id = "TurtleCommander.ToggleSelectionMark")
public class ToggleSelectionMarkAction extends FileManagerAction.Simple {
    public ToggleSelectionMarkAction() {
        super(LocalizeValue.localizeTODO("Toggle Selection (Move Cursor Down)"), "INSERT", FileManagerTab::toggleSelectionAndMoveDown);
    }
}
