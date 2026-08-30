package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabVfsUtil;
import consulo.ui.ex.CopyPasteManager;
import consulo.ui.ex.action.AnActionEvent;

import java.nio.file.Path;

/** Copies the tab directory's parent. */
@ActionImpl(id = "TurtleCommander.TabCopyAsParentPath")
public class TabCopyAsParentPathAction extends TabContextAction {
    public TabCopyAsParentPathAction() {
        super(LocalizeValue.localizeTODO("Parent Path"));
    }

    @Override
    protected void performOnTab(TabContext context, AnActionEvent e) {
        FileTab tab = fileTabOf(context);
        if (tab == null) {
            return;
        }
        if (tab.isInsideArchive()) {
            // The archive's own directory is the meaningful parent of an in-archive location.
            CopyPasteManager.getInstance().setText(tab.getRealFilesystemPath().toString());
            return;
        }
        Path parent = tab.getCurrentPath().getParent();
        CopyPasteManager.getInstance().setText(parent != null ? parent.toString() : "");
    }
}
