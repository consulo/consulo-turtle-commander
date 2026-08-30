package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionRef;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;

/** Checksum submenu of the file context menu's Copy as. */
@ActionImpl(id = "TurtleCommander.CopyAsHashGroup", children = {
    @ActionRef(type = CopyAsCrc16Action.class),
    @ActionRef(type = CopyAsCrc32Action.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = CopyAsMd5Action.class),
    @ActionRef(type = CopyAsSha1Action.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = CopyAsSha256Action.class),
    @ActionRef(type = CopyAsSha512Action.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = CopyAsSha3_256Action.class),
    @ActionRef(type = CopyAsSha3_512Action.class)
})
public class CopyAsHashGroup extends DefaultActionGroup {
    public CopyAsHashGroup() {
        super(LocalizeValue.localizeTODO("Hash"), true);
    }
}
