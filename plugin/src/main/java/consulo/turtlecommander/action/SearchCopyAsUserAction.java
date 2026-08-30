package consulo.turtlecommander.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

/** Copies the hit's owning user. */
@ActionImpl(id = "TurtleCommander.SearchCopyAsUser")
public class SearchCopyAsUserAction extends EntryCopyAction.Attribute {
    public SearchCopyAsUserAction() {
        super(LocalizeValue.localizeTODO("User"), entry -> entry.owner());
    }

    @Override
    protected @Nullable FileEntry entry(AnActionEvent e) {
        return SearchContextMenuState.getClickedEntry(e);
    }
}
