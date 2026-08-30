package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabSelectionUtil;
import consulo.ui.ex.action.AnActionEvent;

import java.util.List;

/** Copies the selected entries as JSON. */
@ActionImpl(id = "TurtleCommander.CopyAsJson")
public class CopyAsJsonAction extends EntryCopyAction.Export {
    public CopyAsJsonAction() {
        super(LocalizeValue.localizeTODO("JSON"),
            entries -> EntryExport.toJson(entries, EntryExport.exportColumnIds()));
    }

    @Override
    protected List<FileEntry> targets(AnActionEvent e) {
        FileTab tab = FileContextMenuState.getClickedTab(e);
        List<FileEntry> selected = tab != null
            ? FileTabSelectionUtil.getSelectedEntries(tab)
            : List.of();
        return withFallbackToClicked(selected, FileContextMenuState.getClickedEntry(e));
    }
}
