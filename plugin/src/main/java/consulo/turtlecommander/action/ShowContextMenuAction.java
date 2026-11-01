package consulo.turtlecommander.action;

import consulo.turtlecommander.ui.FileManagerTab;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;

/** Opens the file context menu from the keyboard. */
@ActionImpl(id = "TurtleCommander.ShowContextMenu")
public class ShowContextMenuAction extends FileManagerAction.Simple {
    public ShowContextMenuAction() {
        super(LocalizeValue.localizeTODO("Show Context Menu"), "CONTEXT_MENU", FileManagerTab::showContextMenu);
    }
}
