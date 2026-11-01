package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.fileEditor.FileEditorManager;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.editor.TctVirtualFile;
import consulo.turtlecommander.editor.TctVirtualFileSystem;
import consulo.turtlecommander.icon.TurtleCommanderIconGroup;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.UnifiedFilePane;
import consulo.turtlecommander.ui.UnifiedFileTab;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import consulo.virtualFileSystem.archive.ArchiveNioFileSystemManager;
import consulo.virtualFileSystem.archive.ArchivePath;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Reveals the file in Turtle Commander, from the "Open In" group of the project view and the editor: a folder opens in
 * the active pane, a file opens its folder with the file selected - inside an archive, through the platform's archive
 * file systems. The Turtle editor is opened first when none is.
 */
@ActionImpl(id = "TurtleCommander.OpenInTurtleCommander", parents = @ActionParentRef(@ActionRef(id = "RevealGroup")))
public class OpenInTurtleCommanderAction extends EdtAction {
    public OpenInTurtleCommanderAction() {
        super(LocalizeValue.localizeTODO("Turtle Commander"), LocalizeValue.empty(), TurtleCommanderIconGroup.turtlecommander());
    }

    @Override
    public void update(AnActionEvent e) {
        VirtualFile file = e.getData(VirtualFile.KEY);
        e.getPresentation().setEnabledAndVisible(e.getData(Project.KEY) != null && file != null && isRevealable(file));
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        VirtualFile file = e.getData(VirtualFile.KEY);
        Path path = file != null ? toPath(file) : null;
        if (project == null || file == null || path == null) {
            return;
        }
        boolean directory = file.isDirectory();

        FileEditorManager editorManager = FileEditorManager.getInstance(project);
        VirtualFile turtleFile = openTurtleFile(editorManager);
        if (turtleFile == null) {
            return;
        }
        editorManager.openFile(turtleFile, true);
        // the view of an editor just opened is built when it is shown, so its panes are asked for afterwards
        UIAccess.current().give(() -> reveal(project, path, directory));
    }

    @RequiredUIAccess
    private static void reveal(Project project, Path path, boolean directory) {
        UnifiedFilePane pane = FileManagerStateService.getInstance(project).getUnifiedActivePane();
        UnifiedFileTab tab = pane != null ? pane.getActiveTab() : null;
        if (tab == null) {
            return;
        }
        if (directory) {
            tab.navigateTo(path);
            return;
        }
        // inside an archive a top level entry's parent is the archive's root
        Path parent = path.getParent();
        if (parent != null) {
            tab.navigateTo(parent, path.getFileName() != null ? path.getFileName().toString() : null);
        }
    }

    /** The Turtle editor already open, or a new one. */
    private static @Nullable VirtualFile openTurtleFile(FileEditorManager editorManager) {
        for (VirtualFile open : editorManager.getOpenFiles()) {
            if (open instanceof TctVirtualFile) {
                return open;
            }
        }
        String url = VirtualFileManager.constructUrl(TctVirtualFileSystem.PROTOCOL, "/" + UUID.randomUUID());
        return VirtualFileManager.getInstance().findFileByUrl(url);
    }

    private static boolean isRevealable(VirtualFile file) {
        return file.getFileSystem() instanceof LocalFileSystem || file.getFileSystem() instanceof ArchiveFileSystem;
    }

    /** The file as a path: on disk, or inside an archive as a path of the platform's archive file system. */
    private static @Nullable Path toPath(VirtualFile file) {
        if (file.getFileSystem() instanceof LocalFileSystem) {
            return file.toNioPath();
        }
        VirtualFile archive = ArchiveVfsUtil.getVirtualFileForArchive(file);
        if (archive == null) {
            return null;
        }
        ArchivePath root = ArchiveNioFileSystemManager.getInstance().getRoot(archive.toNioPath());
        if (root == null) {
            return null;
        }
        String path = file.getPath();
        int separator = path.indexOf("!/");
        String entry = separator >= 0 ? path.substring(separator + 2) : "";
        return entry.isEmpty() ? root : root.resolve(entry);
    }
}
