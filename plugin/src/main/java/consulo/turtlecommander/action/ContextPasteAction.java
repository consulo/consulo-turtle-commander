package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.FileManagerTab;
import consulo.turtlecommander.ui.FileTabOperationUtil;
import consulo.turtlecommander.vfs.VirtualFileSystem;
import consulo.ui.ex.action.AnActionEvent;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/** Copies or moves the buffered entries into the target directory. */
@ActionImpl(id = "TurtleCommander.ContextPaste")
public class ContextPasteAction extends EdtAction {
    public ContextPasteAction() {
        super(LocalizeValue.localizeTODO("Paste"));
        setDefaultShortcut("ctrl V");
    }

    @Override
    public void update(AnActionEvent e) {
        FileManagerTab tab = ActionTargets.resolveFileContextTab(e);
        e.getPresentation().setEnabled(!FileCopyBuffer.getEntries().isEmpty() && isWritable(tab));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileManagerTab tab = ActionTargets.resolveFileContextTab(e);
        if (tab != null) {
            paste(tab, tab.getCurrentPath());
        }
    }

    static boolean isWritable(@Nullable FileManagerTab tab) {
        if (tab == null) {
            return false;
        }
        VirtualFileSystem vfs = tab.getCurrentVfs();
        return vfs == null || !vfs.isReadOnly();
    }

    /** A cut settles the buffer against the outcome; a copy leaves it alone so it can be reused. */
    static void paste(FileManagerTab tab, Path destination) {
        List<FileEntry> entries = FileCopyBuffer.getEntries();
        if (entries.isEmpty()) {
            return;
        }
        String displayPath = tab.getDisplayPath();
        if (FileCopyBuffer.isCut()) {
            FileTabOperationUtil.performMoveEntries(tab, entries, destination, displayPath,
                () -> FileCopyBuffer.pruneMovedEntries(entries));
        }
        else {
            FileTabOperationUtil.performCopyEntries(tab, entries, destination, displayPath);
        }
    }
}
