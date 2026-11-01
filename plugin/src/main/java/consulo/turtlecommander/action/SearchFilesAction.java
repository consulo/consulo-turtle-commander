package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.dialog.FileSearchDialogDescriptor;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.turtlecommander.ui.FileManagerTab;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.dialog.DialogService;

import java.nio.file.Path;
import consulo.turtlecommander.ui.UnifiedFilePane;
import consulo.turtlecommander.ui.UnifiedFileTab;

/** Opens the search form, then a results tab for whatever it returns. */
@ActionImpl(id = "TurtleCommander.SearchFiles")
public class SearchFilesAction extends FileManagerAction {
    public SearchFilesAction() {
        super(LocalizeValue.localizeTODO("Search Files"), "alt F7");
    }

    @Override
    protected void performOnTab(FileManagerTab tab, AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project != null) {
            SearchLauncher.openSearchTab(project, tab.getCurrentPath(), tab);
        }
    }

    /** Shared with the two "Search in Directory" context actions, which differ only in the root. */
    static final class SearchLauncher {
        private SearchLauncher() {
        }

        /** The results open beside {@code from}: in its pane in the unified view, in the active panel otherwise. */
        @RequiredUIAccess
        static void openSearchTab(Project project, Path root, FileManagerTab from) {
            FileSearchDialogDescriptor descriptor = new FileSearchDialogDescriptor(root, null);
            project.getApplication().getInstance(DialogService.class).build(descriptor).showAsync().whenComplete((v, e) -> {
                // a cancelled dialog completes exceptionally
                if (e == null) {
                    FileManagerStateService stateService = FileManagerStateService.getInstance(project);
                    UnifiedFilePane unifiedPane = from instanceof UnifiedFileTab ? stateService.getUnifiedActivePane() : null;
                    if (unifiedPane != null) {
                        unifiedPane.openSearchTab(descriptor.getCriteria());
                        return;
                    }
                    FileManagerPanel panel = stateService.getActivePanel();
                    if (panel != null) {
                        panel.openSearchTab(descriptor.getCriteria());
                    }
                }
            });
        }
    }
}
