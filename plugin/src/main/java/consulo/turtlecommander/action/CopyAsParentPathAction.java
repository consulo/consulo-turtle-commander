package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.FileTab;
import consulo.ui.ex.action.AnActionEvent;
import org.jspecify.annotations.Nullable;

/** Copies the entry's containing directory. */
@ActionImpl(id = "TurtleCommander.CopyAsParentPath")
public class CopyAsParentPathAction extends EntryCopyAction.ParentPath {
    @Override
    protected @Nullable FileEntry entry(AnActionEvent e) {
        return FileContextMenuState.getClickedEntry(e);
    }

    @Override
    protected @Nullable FileTab tab(AnActionEvent e) {
        FileTab clicked = FileContextMenuState.getClickedTab(e);
        return clicked != null ? clicked : ActionTargets.findActiveTab(e);
    }
}
