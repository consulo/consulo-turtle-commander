package consulo.turtlecommander.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

/** Copies the hit's creation timestamp. */
@ActionImpl(id = "TurtleCommander.SearchCopyAsDateCreated")
public class SearchCopyAsDateCreatedAction extends EntryCopyAction.Attribute {
    public SearchCopyAsDateCreatedAction() {
        super(LocalizeValue.localizeTODO("Date Created"), entry -> EntryExport.displayFileTime(entry.creationTime()));
    }

    @Override
    protected @Nullable FileEntry entry(AnActionEvent e) {
        return SearchContextMenuState.getClickedEntry(e);
    }
}
