package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabContextMenuUtil;

/** Opens the file context menu from the keyboard. */
@ActionImpl(id = "TurtleCommander.ShowContextMenu")
public class ShowContextMenuAction extends FileManagerAction.Simple {
    public ShowContextMenuAction() {
        super(LocalizeValue.localizeTODO("Show Context Menu"), "CONTEXT_MENU", FileTabContextMenuUtil::showContextMenuForSelection);
    }
}
