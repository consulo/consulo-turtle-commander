package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabVfsUtil;
import consulo.ui.ex.CopyPasteManager;
import consulo.ui.ex.action.AnActionEvent;

import java.nio.file.Path;

/** Copies the tab's directory name. */
@ActionImpl(id = "TurtleCommander.TabCopyAsName")
public class TabCopyAsNameAction extends TabContextAction {
    public TabCopyAsNameAction() {
        super(LocalizeValue.localizeTODO("Directory Name"));
    }

    @Override
    protected void performOnTab(TabContext context, AnActionEvent e) {
        FileTab tab = fileTabOf(context);
        if (tab == null) {
            return;
        }
        Path fileName = tab.getCurrentPath().getFileName();
        CopyPasteManager.getInstance().setText(
            fileName != null ? fileName.toString() : tab.getCurrentPath().toString());
    }
}
