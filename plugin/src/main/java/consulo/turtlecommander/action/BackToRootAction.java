package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabNavigationUtil;

/** Navigates to the root of the current filesystem or archive. */
@ActionImpl(id = "TurtleCommander.BackToRoot")
public class BackToRootAction extends FileManagerAction.Simple {
    public BackToRootAction() {
        super(LocalizeValue.localizeTODO("Back to Root"), "ctrl BACK_SLASH", FileTabNavigationUtil::goToRoot);
    }
}
