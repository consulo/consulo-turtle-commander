package consulo.turtlecommander.action;

import consulo.turtlecommander.ui.FileManagerTab;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;

/** Navigates to the root of the current filesystem or archive. */
@ActionImpl(id = "TurtleCommander.BackToRoot")
public class BackToRootAction extends FileManagerAction.Simple {
    public BackToRootAction() {
        super(LocalizeValue.localizeTODO("Back to Root"), "ctrl BACK_SLASH", FileManagerTab::goToRoot);
    }
}
