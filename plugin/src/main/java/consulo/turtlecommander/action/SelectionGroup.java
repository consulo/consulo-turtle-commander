package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionRef;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;

/** Bulk marking submenu, shared by the file and tab context menus. */
@ActionImpl(id = "TurtleCommander.SelectionGroup", children = {
    @ActionRef(type = SelectAllAction.class),
    @ActionRef(type = UnselectAllAction.class),
    @ActionRef(type = InvertSelectionAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = SelectByPatternAction.class),
    @ActionRef(type = UnselectByPatternAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = SelectSameExtensionAction.class),
    @ActionRef(type = UnselectSameExtensionAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = SaveSelectionAction.class),
    @ActionRef(type = RestoreSelectionAction.class)
})
public class SelectionGroup extends DefaultActionGroup {
    public SelectionGroup() {
        super(LocalizeValue.localizeTODO("Selection"), true);
    }
}
