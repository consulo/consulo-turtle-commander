package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionRef;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;

/** The right-click menu over search results. */
@ActionImpl(id = "TurtleCommander.SearchContextMenu", children = {
    @ActionRef(type = SearchCopyAsGroup.class)
})
public class SearchContextMenuGroup extends DefaultActionGroup {
    public SearchContextMenuGroup() {
        super(LocalizeValue.localizeTODO("Search Context Menu"), true);
    }
}
