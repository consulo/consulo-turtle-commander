package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.dialog.AboutDialogDescriptor;
import consulo.ui.ex.action.AnActionEvent;
import consulo.application.Application;
import consulo.ui.ex.dialog.DialogService;

/** Shows the plugin's name, version and description. */
@ActionImpl(id = "TurtleCommander.About")
public class AboutAction extends EdtAction {
    public AboutAction() {
        super(LocalizeValue.localizeTODO("About..."));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Application.get().getInstance(DialogService.class).build(new AboutDialogDescriptor()).showAsync();
    }
}
