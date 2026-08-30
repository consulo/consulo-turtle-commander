package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.platform.Platform;
import consulo.ui.ex.action.AnActionEvent;

/** Opens the plugin's online user manual. */
@ActionImpl(id = "TurtleCommander.UserManual")
public class UserManualAction extends EdtAction {
    private static final String USER_MANUAL_URL =
        "https://plugins.jetbrains.com/plugin/30863-turtle-commander/user-manual";

    public UserManualAction() {
        super(LocalizeValue.localizeTODO("User Manual"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Platform.current().openInBrowser(USER_MANUAL_URL);
    }
}
