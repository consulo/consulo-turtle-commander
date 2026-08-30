package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.FileTab;
import consulo.ui.ex.action.AnActionEvent;

/** Pastes into the right-clicked directory rather than the current one. */
@ActionImpl(id = "TurtleCommander.ContextPasteInto")
public class ContextPasteIntoAction extends EdtAction {
    public ContextPasteIntoAction() {
        super(LocalizeValue.localizeTODO("Paste Into"));
    }

    @Override
    public void update(AnActionEvent e) {
        FileEntry entry = FileContextMenuState.getClickedEntry(e);
        FileTab tab = ActionTargets.resolveFileContextTab(e);
        e.getPresentation().setEnabledAndVisible(!FileCopyBuffer.getEntries().isEmpty()
            && entry != null && entry.directory() && !entry.parentLink()
            && ContextPasteAction.isWritable(tab));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileEntry entry = FileContextMenuState.getClickedEntry(e);
        FileTab tab = ActionTargets.resolveFileContextTab(e);
        if (entry != null && tab != null) {
            ContextPasteAction.paste(tab, entry.path());
        }
    }
}
