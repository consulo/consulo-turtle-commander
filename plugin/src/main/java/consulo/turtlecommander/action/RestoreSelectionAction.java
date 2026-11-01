package consulo.turtlecommander.action;

import consulo.turtlecommander.ui.FileManagerTab;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;

/** Restores the marks saved by Save Selection. */
@ActionImpl(id = "TurtleCommander.RestoreSelection")
public class RestoreSelectionAction extends FileManagerAction.Simple {
    public RestoreSelectionAction() {
        super(LocalizeValue.localizeTODO("Restore Selection"), "DIVIDE", FileManagerTab::restoreSavedSelection);
    }
}
