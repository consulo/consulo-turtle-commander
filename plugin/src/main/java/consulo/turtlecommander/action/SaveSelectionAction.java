package consulo.turtlecommander.action;

import consulo.turtlecommander.ui.FileManagerTab;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;

/** Snapshots the current marks. */
@ActionImpl(id = "TurtleCommander.SaveSelection")
public class SaveSelectionAction extends FileManagerAction.Simple {
    public SaveSelectionAction() {
        super(LocalizeValue.localizeTODO("Save Selection"), "ctrl MULTIPLY", FileManagerTab::saveSelection);
    }
}
