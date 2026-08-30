package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionRef;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;

/** The tab context menu's Copy as submenu. */
@ActionImpl(id = "TurtleCommander.TabCopyAsGroup", children = {
    @ActionRef(type = TabCopyAsNameAction.class),
    @ActionRef(type = TabCopyAsFullPathAction.class),
    @ActionRef(type = TabCopyAsParentPathAction.class)
})
public class TabCopyAsGroup extends DefaultActionGroup {
    public TabCopyAsGroup() {
        super(LocalizeValue.localizeTODO("Copy as"), true);
    }
}
