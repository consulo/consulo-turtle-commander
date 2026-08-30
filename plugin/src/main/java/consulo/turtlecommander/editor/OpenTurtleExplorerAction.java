package consulo.turtlecommander.editor;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.fileEditor.FileEditorManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.IdeActions;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;

import java.util.UUID;

/**
 * @author VISTALL
 * @since 2026-08-30
 */
@ActionImpl(id = "OpenTurtleExplorerAction", parents = @ActionParentRef(@ActionRef(id = IdeActions.TOOLS_MENU)))
public class OpenTurtleExplorerAction extends DumbAwareAction {
    public OpenTurtleExplorerAction() {
        super("Open Turtle Explorer");
    }
    
    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }

        String url = VirtualFileManager.constructUrl(TctVirtualFileSystem.PROTOCOL, "/" + UUID.randomUUID());

        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);

        if (file != null) {
            FileEditorManager.getInstance(project).openFile(file, true);
        }
    }
}
