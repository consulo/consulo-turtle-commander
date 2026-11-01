package consulo.turtlecommander.action;

import consulo.turtlecommander.ui.FileManagerTab;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;

/** Marks every displayed entry. */
@ActionImpl(id = "TurtleCommander.SelectAll")
public class SelectAllAction extends FileManagerAction.Simple {
    public SelectAllAction() {
        super(LocalizeValue.localizeTODO("Select All"), "ctrl ADD", FileManagerTab::selectAllEntries);
    }
}
