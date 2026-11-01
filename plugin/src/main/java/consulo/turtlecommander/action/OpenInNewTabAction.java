package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerTab;
import consulo.ui.ex.action.AnActionEvent;
import consulo.turtlecommander.ui.FileManagerTabStrip;
import consulo.turtlecommander.ui.UnifiedFileTab;
import org.jspecify.annotations.Nullable;

/** Opens the right-clicked directory in a new tab. */
@ActionImpl(id = "TurtleCommander.OpenInNewTab")
public class OpenInNewTabAction extends FileContextAction {
    public OpenInNewTabAction() {
        super(LocalizeValue.localizeTODO("Open in New Tab"), null, FileContextAction::isDirectory, true);
    }

    @Override
    protected void performOnTab(FileManagerTab tab, AnActionEvent e) {
        FileEntry entry = FileContextMenuState.getClickedEntry(e);
        FileManagerTabStrip strip = tab instanceof UnifiedFileTab ? unifiedPane(e) : swingPanel(e);
        if (entry != null && strip != null) {
            strip.openDirectoryInNewTab(entry.path(), null);
        }
    }

    private static @Nullable FileManagerTabStrip unifiedPane(AnActionEvent e) {
        FileManagerStateService stateService = ActionTargets.stateService(e);
        return stateService != null ? stateService.getUnifiedActivePane() : null;
    }

    private static @Nullable FileManagerTabStrip swingPanel(AnActionEvent e) {
        FileManagerStateService stateService = ActionTargets.stateService(e);
        return stateService != null ? stateService.getActivePanel() : null;
    }
}
