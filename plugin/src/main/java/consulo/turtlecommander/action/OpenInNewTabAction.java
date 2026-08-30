package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.turtlecommander.ui.FileTab;
import consulo.ui.ex.action.AnActionEvent;

/** Opens the right-clicked directory in a new tab. */
@ActionImpl(id = "TurtleCommander.OpenInNewTab")
public class OpenInNewTabAction extends FileContextAction {
    public OpenInNewTabAction() {
        super(LocalizeValue.localizeTODO("Open in New Tab"), null, FileContextAction::isDirectory, true);
    }

    @Override
    protected void performOnTab(FileTab tab, AnActionEvent e) {
        FileEntry entry = FileContextMenuState.getClickedEntry(e);
        FileManagerStateService stateService = ActionTargets.stateService(e);
        FileManagerPanel panel = stateService != null ? stateService.getActivePanel() : null;
        if (entry != null && panel != null) {
            panel.openDirectoryInNewTab(entry.path(), null);
        }
    }
}
