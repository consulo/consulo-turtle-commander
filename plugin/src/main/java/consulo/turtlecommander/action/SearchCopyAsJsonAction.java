package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.ui.ex.action.AnActionEvent;

import java.util.List;

/** Copies the selected hits as JSON. */
@ActionImpl(id = "TurtleCommander.SearchCopyAsJson")
public class SearchCopyAsJsonAction extends EntryCopyAction.Export {
    public SearchCopyAsJsonAction() {
        super(LocalizeValue.localizeTODO("JSON"),
            entries -> EntryExport.toJson(entries, EntryExport.exportColumnIds()));
    }

    @Override
    protected List<FileEntry> targets(AnActionEvent e) {
        return withFallbackToClicked(SearchContextMenuState.getSelectedEntries(e),
            SearchContextMenuState.getClickedEntry(e));
    }
}
