package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTab;
import consulo.ui.ex.action.AnActionEvent;

/** Reveals the tab's directory in the OS file manager. */
@ActionImpl(id = "TurtleCommander.TabOpenInExplorer")
public class TabOpenInExplorerAction extends TabContextAction {
    public TabOpenInExplorerAction() {
        super(LocalizeValue.localizeTODO("Open in Explorer"));
    }

    @Override
    protected void performOnTab(TabContext context, AnActionEvent e) {
        FileTab tab = fileTabOf(context);
        if (tab != null) {
            // Inside an archive the current path is a temp dir, so reveal the archive's real home.
            OpenInExplorerAction.revealInFileManager(tab.getRealFilesystemPath());
        }
    }
}
