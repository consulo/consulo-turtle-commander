package consulo.turtlecommander.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

/** Copies the hit's owning group. */
@ActionImpl(id = "TurtleCommander.SearchCopyAsFileGroup")
public class SearchCopyAsFileGroupAction extends EntryCopyAction.Attribute {
    public SearchCopyAsFileGroupAction() {
        super(LocalizeValue.localizeTODO("Group"), entry -> entry.group());
    }

    @Override
    protected @Nullable FileEntry entry(AnActionEvent e) {
        return SearchContextMenuState.getClickedEntry(e);
    }
}
