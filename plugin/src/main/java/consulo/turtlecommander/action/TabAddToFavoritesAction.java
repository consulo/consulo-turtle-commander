package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileTab;
import consulo.ui.ex.action.AnActionEvent;

/** Pins the tab's directory to the tool window header. */
@ActionImpl(id = "TurtleCommander.TabAddToFavorites")
public class TabAddToFavoritesAction extends TabContextAction {
    public TabAddToFavoritesAction() {
        super(LocalizeValue.localizeTODO("Add to Favorites"));
    }

    @Override
    protected void performOnTab(TabContext context, AnActionEvent e) {
        FileTab tab = fileTabOf(context);
        FileManagerStateService stateService = ActionTargets.stateService(e);
        if (tab != null && stateService != null) {
            stateService.addFavorite(tab.getCurrentPath().toString(), "");
        }
    }
}
