package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.dialog.FileSearchDialog;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.turtlecommander.ui.FileTab;
import consulo.ui.ex.action.AnActionEvent;

import java.nio.file.Path;

/** Opens the search form, then a results tab for whatever it returns. */
@ActionImpl(id = "TurtleCommander.SearchFiles")
public class SearchFilesAction extends FileManagerAction {
    public SearchFilesAction() {
        super(LocalizeValue.localizeTODO("Search Files"), "alt F7");
    }

    @Override
    protected void performOnTab(FileTab tab, AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project != null) {
            SearchLauncher.openSearchTab(project, tab.getCurrentPath());
        }
    }

    /** Shared with the two "Search in Directory" context actions, which differ only in the root. */
    static final class SearchLauncher {
        private SearchLauncher() {
        }

        static void openSearchTab(Project project, Path root) {
            FileSearchDialog dialog = new FileSearchDialog(project, root, null);
            if (!dialog.showAndGet()) {
                return;
            }
            FileManagerPanel panel = FileManagerStateService.getInstance(project).getActivePanel();
            if (panel != null) {
                panel.openSearchTab(dialog.getCriteria());
            }
        }
    }
}
