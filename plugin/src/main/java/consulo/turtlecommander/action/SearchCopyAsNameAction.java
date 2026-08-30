package consulo.turtlecommander.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.annotation.component.ActionImpl;
import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

/** Copies the hit's name. */
@ActionImpl(id = "TurtleCommander.SearchCopyAsName")
public class SearchCopyAsNameAction extends EntryCopyAction.Name {
    @Override
    protected @Nullable FileEntry entry(AnActionEvent e) {
        return SearchContextMenuState.getClickedEntry(e);
    }
}
