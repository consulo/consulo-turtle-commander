package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabSelectionUtil;
import consulo.ui.ex.action.AnActionEvent;

/** Puts the selection in the copy buffer, marked for moving. */
@ActionImpl(id = "TurtleCommander.ContextCut")
public class ContextCutAction extends EdtAction {
    public ContextCutAction() {
        super(LocalizeValue.localizeTODO("Cut"));
        setDefaultShortcut("ctrl X");
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
            FileCopyBuffer.setCut(true);
            FileCopyBuffer.setEntries(FileTabSelectionUtil.getSelectedEntries(tab));
        }
    }
}
