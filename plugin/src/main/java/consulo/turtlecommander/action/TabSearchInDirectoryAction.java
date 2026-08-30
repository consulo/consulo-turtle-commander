package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.ui.FileTab;
import consulo.ui.ex.action.AnActionEvent;

/** Starts a search rooted at the tab's directory. */
@ActionImpl(id = "TurtleCommander.TabSearchInDirectory")
public class TabSearchInDirectoryAction extends TabContextAction {
    public TabSearchInDirectoryAction() {
        super(LocalizeValue.localizeTODO("Search in Directory..."));
    }

    @Override
    protected void performOnTab(TabContext context, AnActionEvent e) {
        FileTab tab = fileTabOf(context);
        Project project = e.getData(Project.KEY);
        if (tab != null && project != null) {
            SearchFilesAction.SearchLauncher.openSearchTab(project, tab.getCurrentPath());
        }
    }
}
