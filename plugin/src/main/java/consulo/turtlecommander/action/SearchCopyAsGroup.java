package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionRef;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;

/** The search results' Copy as submenu. */
@ActionImpl(id = "TurtleCommander.SearchCopyAsGroup", children = {
    @ActionRef(type = SearchCopyAsNameAction.class),
    @ActionRef(type = SearchCopyAsFullPathAction.class),
    @ActionRef(type = SearchCopyAsParentPathAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = SearchCopyAsDateCreatedAction.class),
    @ActionRef(type = SearchCopyAsDateModifiedAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = SearchCopyAsUserAction.class),
    @ActionRef(type = SearchCopyAsFileGroupAction.class),
    @ActionRef(type = SearchCopyAsUserGroupAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = SearchCopyAsCsvAction.class),
    @ActionRef(type = SearchCopyAsJsonAction.class)
})
public class SearchCopyAsGroup extends DefaultActionGroup {
    public SearchCopyAsGroup() {
        super(LocalizeValue.localizeTODO("Copy as"), true);
    }
}
