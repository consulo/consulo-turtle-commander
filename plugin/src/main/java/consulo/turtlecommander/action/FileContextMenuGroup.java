package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionRef;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;

/** The right-click menu over a file listing. */
@ActionImpl(id = "TurtleCommander.FileContextMenu", children = {
    @ActionRef(type = OpenInNewTabAction.class),
    @ActionRef(type = OpenFileAction.class),
    @ActionRef(type = OpenInAssociatedAppAction.class),
    @ActionRef(type = OpenInExplorerAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = ContextCopyAction.class),
    @ActionRef(type = ContextCutAction.class),
    @ActionRef(type = ContextPasteAction.class),
    @ActionRef(type = ContextPasteIntoAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = CopyAsGroup.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = SelectionGroup.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = ContextRenameAction.class),
    @ActionRef(type = ContextMultiRenameAction.class),
    @ActionRef(type = ContextCreateLinkAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = ContextDeleteAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = PackFilesAction.class),
    @ActionRef(type = ExtractFilesAction.class),
    @ActionRef(type = ExtractHereAction.class),
    @ActionRef(type = ExtractToSubdirAction.class),
    @ActionRef(type = SplitFileAction.class),
    @ActionRef(type = CombineFilesAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = CompareFilesAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = ContextSearchInDirectoryAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = AddToFavoritesAction.class),
    @ActionRef(type = AnSeparator.class),
    @ActionRef(type = ShowPropertiesAction.class)
})
public class FileContextMenuGroup extends DefaultActionGroup {
    public FileContextMenuGroup() {
        super(LocalizeValue.localizeTODO("File Context Menu"), true);
    }
}
