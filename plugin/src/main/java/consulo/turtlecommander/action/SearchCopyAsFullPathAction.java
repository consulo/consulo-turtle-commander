package consulo.turtlecommander.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.annotation.component.ActionImpl;
import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

/** Copies the hit's full path. */
@ActionImpl(id = "TurtleCommander.SearchCopyAsFullPath")
public class SearchCopyAsFullPathAction extends EntryCopyAction.FullPath {
    @Override
    protected @Nullable FileEntry entry(AnActionEvent e) {
        return SearchContextMenuState.getClickedEntry(e);
    }
}
