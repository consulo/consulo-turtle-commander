package consulo.turtlecommander.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

/** Copies the hit's modification timestamp. */
@ActionImpl(id = "TurtleCommander.SearchCopyAsDateModified")
public class SearchCopyAsDateModifiedAction extends EntryCopyAction.Attribute {
    public SearchCopyAsDateModifiedAction() {
        super(LocalizeValue.localizeTODO("Date Modified"), entry -> EntryExport.displayFileTime(entry.lastModified()));
    }

    @Override
    protected @Nullable FileEntry entry(AnActionEvent e) {
        return SearchContextMenuState.getClickedEntry(e);
    }
}
