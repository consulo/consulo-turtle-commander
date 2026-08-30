package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabSelectionUtil;

/** Marks every displayed entry. */
@ActionImpl(id = "TurtleCommander.SelectAll")
public class SelectAllAction extends FileManagerAction.Simple {
    public SelectAllAction() {
        super(LocalizeValue.localizeTODO("Select All"), "ctrl ADD", FileTabSelectionUtil::selectAllEntries);
    }
}
