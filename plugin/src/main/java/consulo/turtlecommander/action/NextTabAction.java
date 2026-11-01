package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.ui.ex.action.AnActionEvent;
import consulo.turtlecommander.ui.UnifiedFilePane;
import consulo.turtlecommander.ui.UnifiedFileTab;

/** Selects the next tab, wrapping at the end. */
@ActionImpl(id = "TurtleCommander.NextTab")
public class NextTabAction extends FileManagerAction.ToolWindowScoped implements UnifiedPaneAction {
    public NextTabAction() {
        super(LocalizeValue.localizeTODO("Next Tab"), "alt RIGHT");
    }

    /** In the unified view the pane in use is the target, while the Turtle editor is where the input goes. */
    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(ActionTargets.isToolWindowActive(e) || ActionTargets.findActiveTab(e) instanceof UnifiedFileTab);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileManagerStateService stateService = ActionTargets.stateService(e);
        if (stateService == null) {
            return;
        }
        UnifiedFilePane unifiedPane = stateService.getUnifiedActivePane();
        if (unifiedPane != null) {
            unifiedPane.selectNextTab();
            return;
        }
        FileManagerPanel panel = stateService.getActivePanel();
        if (panel != null) {
            panel.selectNextTab();
        }
    }
}
