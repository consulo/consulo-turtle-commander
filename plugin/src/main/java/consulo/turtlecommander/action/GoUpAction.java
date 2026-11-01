package consulo.turtlecommander.action;

import consulo.turtlecommander.ui.FileManagerTab;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;

/** Navigates to the parent directory, or leaves the current archive. */
@ActionImpl(id = "TurtleCommander.GoUp")
public class GoUpAction extends FileManagerAction.Simple {
    public GoUpAction() {
        super(LocalizeValue.localizeTODO("Go Up"), "BACK_SPACE", FileManagerTab::goUp);
    }
}
