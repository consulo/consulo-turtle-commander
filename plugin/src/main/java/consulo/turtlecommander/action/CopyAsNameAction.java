package consulo.turtlecommander.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.annotation.component.ActionImpl;
import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

/** Copies the entry's name. */
@ActionImpl(id = "TurtleCommander.CopyAsName")
public class CopyAsNameAction extends EntryCopyAction.Name {
    @Override
    protected @Nullable FileEntry entry(AnActionEvent e) {
        return FileContextMenuState.getClickedEntry(e);
    }
}
