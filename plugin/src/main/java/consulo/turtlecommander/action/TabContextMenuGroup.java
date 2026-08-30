package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionRef;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;

/** The right-click menu over the tab strip. */
@ActionImpl(id = "TurtleCommander.TabContextMenu", children = {
    @ActionRef(type = CloseTabAction.class),
    @ActionRef(type = CloseOtherTabsAction.class),
    @ActionRef(type = CloseAllTabsAction.class),
    @ActionRef(type = CloseDuplicateTabsAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = CloseTabsToTheLeftAction.class),
    @ActionRef(type = CloseTabsToTheRightAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = DuplicateTabAction.class),
    @ActionRef(type = TabRefreshAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = TabCopyAsGroup.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = SelectionGroup.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = TabOpenInExplorerAction.class),
    @ActionRef(type = TabSearchInDirectoryAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = TabAddToFavoritesAction.class)
})
public class TabContextMenuGroup extends DefaultActionGroup {
    public TabContextMenuGroup() {
        super(LocalizeValue.localizeTODO("Tab Context Menu"), true);
    }
}
