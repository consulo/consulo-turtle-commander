package consulo.turtlecommander.action;

import consulo.turtlecommander.ui.FileManagerTab;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;

/** Inverts the marks over the displayed entries. */
@ActionImpl(id = "TurtleCommander.InvertSelection")
public class InvertSelectionAction extends FileManagerAction.Simple {
    public InvertSelectionAction() {
        super(LocalizeValue.localizeTODO("Invert Selection"), "MULTIPLY", FileManagerTab::invertSelection);
    }
}
