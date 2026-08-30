package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.toolWindow.ToolWindow;

/** Hides the tool window. */
@ActionImpl(id = "TurtleCommander.Quit")
public class QuitAction extends FileManagerAction.ToolWindowScoped {
    public QuitAction() {
        super(LocalizeValue.localizeTODO("Quit"), "F10");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        ToolWindow toolWindow = ActionTargets.toolWindow(project);
        if (toolWindow != null) {
            toolWindow.hide();
        }
    }
}
