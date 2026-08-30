package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.dialog.AboutDialog;
import consulo.ui.ex.action.AnActionEvent;

/** Shows the plugin's name, version and description. */
@ActionImpl(id = "TurtleCommander.About")
public class AboutAction extends EdtAction {
    public AboutAction() {
        super(LocalizeValue.localizeTODO("About..."));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        new AboutDialog(project).show();
    }
}
