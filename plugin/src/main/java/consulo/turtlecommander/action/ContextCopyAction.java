package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabSelectionUtil;
import consulo.ui.ex.action.AnActionEvent;

/** Puts the selection in the copy buffer. */
@ActionImpl(id = "TurtleCommander.ContextCopy")
public class ContextCopyAction extends EdtAction {
    public ContextCopyAction() {
        super(LocalizeValue.localizeTODO("Copy"));
        setDefaultShortcut("ctrl C");
    }

    @Override
    public void update(AnActionEvent e) {
        FileTab tab = ActionTargets.resolveFileContextTab(e);
        e.getPresentation().setEnabled(tab != null && !FileTabSelectionUtil.getSelectedEntries(tab).isEmpty());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileTab tab = ActionTargets.resolveFileContextTab(e);
        if (tab != null) {
            FileCopyBuffer.setCut(false);
            FileCopyBuffer.setEntries(FileTabSelectionUtil.getSelectedEntries(tab));
        }
    }
}
