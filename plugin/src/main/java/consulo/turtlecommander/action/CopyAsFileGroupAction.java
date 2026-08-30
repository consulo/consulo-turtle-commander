package consulo.turtlecommander.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

/** Copies the entry's owning group. */
@ActionImpl(id = "TurtleCommander.CopyAsFileGroup")
public class CopyAsFileGroupAction extends EntryCopyAction.Attribute {
    public CopyAsFileGroupAction() {
        super(LocalizeValue.localizeTODO("Group"), entry -> entry.group());
    }

    @Override
    protected @Nullable FileEntry entry(AnActionEvent e) {
        return FileContextMenuState.getClickedEntry(e);
    }
}
