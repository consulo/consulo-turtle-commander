package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.FileTab;
import consulo.ui.ex.action.AnActionEvent;

/** Starts a search rooted at the right-clicked directory. */
@ActionImpl(id = "TurtleCommander.SearchInDirectory")
public class ContextSearchInDirectoryAction extends FileContextAction {
    public ContextSearchInDirectoryAction() {
        super(LocalizeValue.localizeTODO("Search in Directory..."), null,
            FileContextAction::isDirectory, true);
    }

    @Override
    protected void performOnTab(FileTab tab, AnActionEvent e) {
        FileEntry entry = FileContextMenuState.getClickedEntry(e);
        Project project = e.getData(Project.KEY);
        if (entry != null && project != null) {
            SearchFilesAction.SearchLauncher.openSearchTab(project, entry.path());
        }
    }
}
