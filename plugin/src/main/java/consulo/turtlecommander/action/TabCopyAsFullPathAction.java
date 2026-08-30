package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabVfsUtil;
import consulo.ui.ex.CopyPasteManager;
import consulo.ui.ex.action.AnActionEvent;

import java.nio.file.Path;

/** Copies the tab's full path. */
@ActionImpl(id = "TurtleCommander.TabCopyAsFullPath")
public class TabCopyAsFullPathAction extends TabContextAction {
    public TabCopyAsFullPathAction() {
        super(LocalizeValue.localizeTODO("Full Path"));
    }

    @Override
    protected void performOnTab(TabContext context, AnActionEvent e) {
        FileTab tab = fileTabOf(context);
        if (tab == null) {
            return;
        }
        // Inside an archive the display path is the archive chain, which is what the user sees.
        CopyPasteManager.getInstance().setText(tab.isInsideArchive()
            ? FileTabVfsUtil.getDisplayPath(tab)
            : tab.getCurrentPath().toString());
    }
}
