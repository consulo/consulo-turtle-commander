package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabSelectionUtil;

/** Inverts the marks over the displayed entries. */
@ActionImpl(id = "TurtleCommander.InvertSelection")
public class InvertSelectionAction extends FileManagerAction.Simple {
    public InvertSelectionAction() {
        super(LocalizeValue.localizeTODO("Invert Selection"), "MULTIPLY", FileTabSelectionUtil::invertSelectionEntries);
    }
}
