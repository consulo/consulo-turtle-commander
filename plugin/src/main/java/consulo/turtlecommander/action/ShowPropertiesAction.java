package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.application.util.SystemInfo;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.dialog.PropertiesDialog;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.util.NativeProperties;
import consulo.turtlecommander.util.UiTask;
import consulo.ui.ex.action.AnActionEvent;

/**
 * Shows the platform's Properties / Get Info dialog for the right-clicked entry, falling back to a
 * read-only Swing dialog when no native one is available or the entry lives inside an archive.
 *
 * <p>Single-target only: right-click is implicitly single-target, and the combined multi-item
 * dialog would need lower-level shell APIs.
 */
@ActionImpl(id = "TurtleCommander.ShowProperties")
public class ShowPropertiesAction extends FileContextAction {
    public ShowPropertiesAction() {
        super(LocalizeValue.localizeTODO("Properties"), null, FileContextAction::isRealEntry, true);
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setText(LocalizeValue.localizeTODO(SystemInfo.isMac ? "Get Info" : "Properties"));
    }

    @Override
    protected void performOnTab(FileTab tab, AnActionEvent e) {
        FileEntry entry = FileContextMenuState.getClickedEntry(e);
        if (entry == null) {
            return;
        }
        Project project = e.getData(Project.KEY);
        // Inside an archive the path points at a temp-extracted copy, whose size, dates and owner
        // don't match the archive entry — go straight to the Swing dialog, which reads the entry
        // record we already have.
        boolean canUseNative = !tab.isInsideArchive();

        // The native call shells out and can block for up to a second, so it runs off the UI thread
        // and only the fallback dialog hops back.
        UiTask.background(() -> {
            boolean opened = canUseNative && NativeProperties.showProperties(entry.path());
            if (!opened) {
                UiTask.ui(() -> new PropertiesDialog(project, entry).show());
            }
        });
    }
}
