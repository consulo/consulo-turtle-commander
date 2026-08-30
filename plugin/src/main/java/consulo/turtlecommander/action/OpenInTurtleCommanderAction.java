package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.virtualFileSystem.VirtualFile;

import java.nio.file.Path;

/** Reveals a file picked elsewhere in the IDE — the project tree, an editor tab — in a new tab. */
@ActionImpl(id = "TurtleCommander.OpenInTurtleCommander")
public class OpenInTurtleCommanderAction extends EdtAction {
    public OpenInTurtleCommanderAction() {
        super(LocalizeValue.localizeTODO("Turtle Commander"));
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getData(VirtualFile.KEY) != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        VirtualFile file = e.getData(VirtualFile.KEY);
        if (project == null || file == null) {
            return;
        }
        ToolWindow toolWindow = ActionTargets.toolWindow(project);
        if (toolWindow == null) {
            return;
        }
        Path path = Path.of(file.getPath());
        boolean directory = file.isDirectory();

        // Activation is asynchronous, and the panels only exist once the content is built — so the
        // navigation has to run in the callback rather than straight after this call.
        toolWindow.activate(() -> {
            FileManagerPanel panel = FileManagerStateService.getInstance(project).getActivePanel();
            if (panel == null) {
                return;
            }
            if (directory) {
                panel.openDirectoryInNewTab(path, null);
            }
            else {
                Path parent = path.getParent();
                if (parent != null) {
                    panel.openDirectoryInNewTab(parent, path.getFileName().toString());
                }
            }
        });
    }
}
