package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.dataContext.DataContext;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.FileManagerTab;
import consulo.turtlecommander.util.PlatformPaths;
import consulo.turtlecommander.util.UiTask;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Opens a terminal in the right-clicked folder - or the folder of the right-clicked file - on the machine the folder is
 * on. The terminal plugin does it; without that plugin the action is hidden.
 */
@ActionImpl(id = "TurtleCommander.OpenInTerminal")
public class OpenInTerminalAction extends FileContextAction {
    /** The terminal plugin's own action - it opens the terminal on the machine of the file it is given. */
    private static final String TERMINAL_ACTION_ID = "Terminal.OpenInTerminal";

    public OpenInTerminalAction() {
        super(LocalizeValue.localizeTODO("Open in Terminal"), null, FileContextAction::isRealEntry, false);
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        if (terminalAction() == null) {
            e.getPresentation().setEnabledAndVisible(false);
        }
    }

    @Override
    protected void performOnTab(FileManagerTab tab, AnActionEvent e) {
        FileEntry entry = FileContextMenuState.getClickedEntry(e);
        if (entry == null || tab.isInsideArchive()) {
            return;
        }
        Path directory = entry.directory() ? entry.path() : entry.path().getParent();
        if (directory == null) {
            return;
        }
        Project project = tab.getProject();
        String place = e.getPlace();
        // finding the folder of another machine asks that machine
        UiTask.background(() -> {
            VirtualFile folder = PlatformPaths.findVirtualFile(directory, true);
            if (folder == null) {
                return;
            }
            tab.getUIAccess().give(() -> {
                AnAction terminal = terminalAction();
                if (terminal == null) {
                    return;
                }
                DataContext context = DataContext.builder()
                    .add(Project.KEY, project)
                    .add(VirtualFile.KEY, folder)
                    .build();
                terminal.actionPerformed(AnActionEvent.createFromDataContext(place, null, context));
            });
        });
    }

    private static @Nullable AnAction terminalAction() {
        return ActionManager.getInstance().getAction(TERMINAL_ACTION_ID);
    }
}
