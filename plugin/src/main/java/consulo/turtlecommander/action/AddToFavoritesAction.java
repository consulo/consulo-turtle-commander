package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileTab;
import consulo.ui.ex.action.AnActionEvent;

/** Pins the right-clicked directory to the tool window header. */
@ActionImpl(id = "TurtleCommander.AddToFavorites")
public class AddToFavoritesAction extends FileContextAction {
    public AddToFavoritesAction() {
        super(LocalizeValue.localizeTODO("Add to Favorites"), null, FileContextAction::isDirectory, true);
    }

    @Override
    protected void performOnTab(FileTab tab, AnActionEvent e) {
        FileEntry entry = FileContextMenuState.getClickedEntry(e);
        FileManagerStateService stateService = ActionTargets.stateService(e);
        if (entry != null && stateService != null) {
            stateService.addFavorite(entry.path().toString(), "");
        }
    }
}
