package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;

/** Opens the IDE settings on the plugin's own page. */
@ActionImpl(id = "TurtleCommander.OpenPluginSettings")
public class OpenPluginSettingsAction extends EdtAction {
    /** Shared with the Configurable so the action and the page can't drift apart. */
    public static final String CONFIGURABLE_ID = "consulo.turtlecommander.settings";

    public OpenPluginSettingsAction() {
        super(LocalizeValue.localizeTODO("Settings..."));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project != null) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, CONFIGURABLE_ID, null);
        }
    }
}
