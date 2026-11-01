package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnActionEvent;
import consulo.turtlecommander.ui.FileManagerTabStrip;

/** Reopens the most recently closed tab, back in its original position. */
@ActionImpl(id = "TurtleCommander.ReopenTab")
public class ReopenTabAction extends FileManagerAction.ToolWindowScoped implements UnifiedPaneAction {
    public ReopenTabAction() {
        super(LocalizeValue.localizeTODO("Reopen Closed Tab"), "ctrl shift T");
    }

    @Override
    public void update(AnActionEvent e) {
        FileManagerTabStrip strip = ActionTargets.findActiveTabStrip(e);
        e.getPresentation().setEnabled(strip != null && strip.hasClosedTabs());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileManagerTabStrip strip = ActionTargets.findActiveTabStrip(e);
        if (strip != null) {
            strip.reopenLastClosedTab();
        }
    }
}
