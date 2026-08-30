package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;

/** Opens the keymap settings, filtered to this plugin's actions. */
@ActionImpl(id = "TurtleCommander.OpenKeymapSettings")
public class OpenKeymapSettingsAction extends EdtAction {
    private static final String KEYMAP_CONFIGURABLE_ID = "preferences.keymap";

    public OpenKeymapSettingsAction() {
        super(LocalizeValue.localizeTODO("Keyboard Shortcuts..."));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project != null) {
            // The filter drives the keymap page's own search box, which is how the user lands on
            // this plugin's actions without having to expand the tree by hand.
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, KEYMAP_CONFIGURABLE_ID, "Turtle Commander");
        }
    }
}
