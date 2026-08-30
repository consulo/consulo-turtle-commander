package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabSelectionUtil;

/** Clears every mark. */
@ActionImpl(id = "TurtleCommander.UnselectAll")
public class UnselectAllAction extends FileManagerAction.Simple {
    public UnselectAllAction() {
        super(LocalizeValue.localizeTODO("Unselect All"), "ctrl SUBTRACT", FileTabSelectionUtil::unselectAllEntries);
    }
}
