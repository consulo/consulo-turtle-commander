package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileManagerTab;
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
        FileManagerTab tab = ActionTargets.resolveFileContextTab(e);
        e.getPresentation().setEnabled(tab != null && !tab.getSelectedEntries().isEmpty());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileManagerTab tab = ActionTargets.resolveFileContextTab(e);
        if (tab != null) {
            FileCopyBuffer.setCut(false);
            FileCopyBuffer.setEntries(tab.getSelectedEntries());
        }
    }
}
