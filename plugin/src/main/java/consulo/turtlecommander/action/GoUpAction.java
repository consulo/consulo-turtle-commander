package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabNavigationUtil;

/** Navigates to the parent directory, or leaves the current archive. */
@ActionImpl(id = "TurtleCommander.GoUp")
public class GoUpAction extends FileManagerAction.Simple {
    public GoUpAction() {
        super(LocalizeValue.localizeTODO("Go Up"), "BACK_SPACE", FileTabNavigationUtil::goUp);
    }
}
