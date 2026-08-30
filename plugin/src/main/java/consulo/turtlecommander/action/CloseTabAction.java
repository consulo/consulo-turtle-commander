package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnActionEvent;

/** Closes the right-clicked tab, or the active one when triggered by shortcut. */
@ActionImpl(id = "TurtleCommander.CloseTab")
public class CloseTabAction extends TabContextAction {
    public CloseTabAction() {
        super(LocalizeValue.localizeTODO("Close"));
        setDefaultShortcut("ctrl W");
    }

    @Override
    public void update(AnActionEvent e) {
        // A panel always keeps at least one tab.
        TabContext context = resolveTabContext(e);
        e.getPresentation().setEnabled(context != null && context.panel().getRealTabCount() > 1);
    }

    @Override
    protected void performOnTab(TabContext context, AnActionEvent e) {
        context.panel().closeTab(context.tabIndex());
    }
}
