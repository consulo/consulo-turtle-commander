package consulo.turtlecommander.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

/** Copies the hit's owner and group. */
@ActionImpl(id = "TurtleCommander.SearchCopyAsUserGroup")
public class SearchCopyAsUserGroupAction extends EntryCopyAction.Attribute {
    public SearchCopyAsUserGroupAction() {
        super(LocalizeValue.localizeTODO("User:Group"), entry -> EntryExport.userGroupOf(entry));
    }

    @Override
    protected @Nullable FileEntry entry(AnActionEvent e) {
        return SearchContextMenuState.getClickedEntry(e);
    }
}
