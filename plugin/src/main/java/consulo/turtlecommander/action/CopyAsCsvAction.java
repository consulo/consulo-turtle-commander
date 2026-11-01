package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.FileManagerTab;
import consulo.ui.ex.action.AnActionEvent;

import java.util.List;

/** Copies the selected entries as CSV. */
@ActionImpl(id = "TurtleCommander.CopyAsCsv")
public class CopyAsCsvAction extends EntryCopyAction.Export {
    public CopyAsCsvAction() {
        super(LocalizeValue.localizeTODO("CSV"),
            entries -> EntryExport.toCsv(entries, EntryExport.exportColumnIds()));
    }

    @Override
    protected List<FileEntry> targets(AnActionEvent e) {
        FileManagerTab tab = FileContextMenuState.getClickedTab(e);
        List<FileEntry> selected = tab != null
            ? tab.getSelectedEntries()
            : List.of();
        return withFallbackToClicked(selected, FileContextMenuState.getClickedEntry(e));
    }
}
