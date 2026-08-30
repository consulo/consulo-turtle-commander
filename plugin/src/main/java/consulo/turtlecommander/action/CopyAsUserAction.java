package consulo.turtlecommander.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

/** Copies the entry's owning user. */
@ActionImpl(id = "TurtleCommander.CopyAsUser")
public class CopyAsUserAction extends EntryCopyAction.Attribute {
    public CopyAsUserAction() {
        super(LocalizeValue.localizeTODO("User"), entry -> entry.owner());
    }

    @Override
    protected @Nullable FileEntry entry(AnActionEvent e) {
        return FileContextMenuState.getClickedEntry(e);
    }
}
