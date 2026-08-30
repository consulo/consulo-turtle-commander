package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTabOperationUtil;

/** Links the selection into the other panel. */
@ActionImpl(id = "TurtleCommander.ContextCreateLink")
public class ContextCreateLinkAction extends FileContextAction.Simple {
    public ContextCreateLinkAction() {
        super(LocalizeValue.localizeTODO("Create Link..."), null, (tab, entry) -> FileContextAction.isRealEntry(tab, entry) && !tab.isInsideArchive(), true, FileTabOperationUtil::performCreateLink);
    }
}
