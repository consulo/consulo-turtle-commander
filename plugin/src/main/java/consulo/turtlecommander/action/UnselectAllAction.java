package consulo.turtlecommander.action;

import consulo.turtlecommander.ui.FileManagerTab;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;

/** Clears every mark. */
@ActionImpl(id = "TurtleCommander.UnselectAll")
public class UnselectAllAction extends FileManagerAction.Simple {
    public UnselectAllAction() {
        super(LocalizeValue.localizeTODO("Unselect All"), "ctrl SUBTRACT", FileManagerTab::unselectAllEntries);
    }
}
