package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.ui.ex.action.AnActionEvent;

import java.util.List;

/** Copies the selected hits as CSV. */
@ActionImpl(id = "TurtleCommander.SearchCopyAsCsv")
public class SearchCopyAsCsvAction extends EntryCopyAction.Export {
    public SearchCopyAsCsvAction() {
        super(LocalizeValue.localizeTODO("CSV"),
            entries -> EntryExport.toCsv(entries, EntryExport.exportColumnIds()));
    }

    @Override
    protected List<FileEntry> targets(AnActionEvent e) {
        return withFallbackToClicked(SearchContextMenuState.getSelectedEntries(e),
            SearchContextMenuState.getClickedEntry(e));
    }
}
