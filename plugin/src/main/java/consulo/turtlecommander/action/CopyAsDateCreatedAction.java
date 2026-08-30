package consulo.turtlecommander.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

/** Copies the entry's creation timestamp. */
@ActionImpl(id = "TurtleCommander.CopyAsDateCreated")
public class CopyAsDateCreatedAction extends EntryCopyAction.Attribute {
    public CopyAsDateCreatedAction() {
        super(LocalizeValue.localizeTODO("Date Created"), entry -> EntryExport.displayFileTime(entry.creationTime()));
    }

    @Override
    protected @Nullable FileEntry entry(AnActionEvent e) {
        return FileContextMenuState.getClickedEntry(e);
    }
}
