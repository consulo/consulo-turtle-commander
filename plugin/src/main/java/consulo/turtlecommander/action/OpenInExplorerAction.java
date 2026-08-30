package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.util.UiTask;
import consulo.ui.ex.action.AnActionEvent;

import java.awt.Desktop;
import java.nio.file.Path;

/** Reveals the right-clicked entry in the OS file manager. */
@ActionImpl(id = "TurtleCommander.OpenInExplorer")
public class OpenInExplorerAction extends FileContextAction {
    public OpenInExplorerAction() {
        super(LocalizeValue.localizeTODO("Open in Explorer"), null, FileContextAction::isRealEntry, false);
    }

    @Override
    protected void performOnTab(FileTab tab, AnActionEvent e) {
        FileEntry entry = FileContextMenuState.getClickedEntry(e);
        if (entry != null) {
            revealInFileManager(entry.path());
        }
    }

    /**
     * Opens the containing directory. {@code Desktop.browseFileDirectory} would select the file
     * itself, but it is unimplemented on most Linux desktops, so the parent is opened instead —
     * which is what the platforms without it can actually do.
     */
    static void revealInFileManager(Path path) {
        Path directory = java.nio.file.Files.isDirectory(path) ? path : path.getParent();
        if (directory == null) {
            return;
        }
        // Desktop.open blocks while it resolves the handler; the JDK warns against the UI thread.
        UiTask.background(() -> {
            try {
                Desktop.getDesktop().open(directory.toFile());
            }
            catch (Exception e) {
                // No desktop integration available — nothing useful to report to the user.
            }
        });
    }
}
