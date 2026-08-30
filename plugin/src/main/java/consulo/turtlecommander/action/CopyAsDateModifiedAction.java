package consulo.turtlecommander.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

/** Copies the entry's modification timestamp. */
@ActionImpl(id = "TurtleCommander.CopyAsDateModified")
public class CopyAsDateModifiedAction extends EntryCopyAction.Attribute {
    public CopyAsDateModifiedAction() {
        super(LocalizeValue.localizeTODO("Date Modified"), entry -> EntryExport.displayFileTime(entry.lastModified()));
    }

    @Override
    protected @Nullable FileEntry entry(AnActionEvent e) {
        return FileContextMenuState.getClickedEntry(e);
    }
}
