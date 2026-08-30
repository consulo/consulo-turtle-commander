package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionRef;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;

/** The file context menu's Copy as submenu. */
@ActionImpl(id = "TurtleCommander.CopyAsGroup", children = {
    @ActionRef(type = CopyAsNameAction.class),
    @ActionRef(type = CopyAsFullPathAction.class),
    @ActionRef(type = CopyAsParentPathAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = CopyAsDateCreatedAction.class),
    @ActionRef(type = CopyAsDateModifiedAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = CopyAsUserAction.class),
    @ActionRef(type = CopyAsFileGroupAction.class),
    @ActionRef(type = CopyAsUserGroupAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = CopyAsCsvAction.class),
    @ActionRef(type = CopyAsJsonAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = CopyAsHashGroup.class)
})
public class CopyAsGroup extends DefaultActionGroup {
    public CopyAsGroup() {
        super(LocalizeValue.localizeTODO("Copy as"), true);
    }
}
