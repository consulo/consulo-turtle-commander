package consulo.turtlecommander.util;

import consulo.application.Application;
import consulo.application.platform.PlatformTarget;
import consulo.application.platform.PlatformTargetManager;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import org.jspecify.annotations.Nullable;

import java.nio.file.FileSystems;
import java.nio.file.Path;

/**
 * Paths of the machines a project can live on - the local one or another one reached through a platform target. A path
 * of another machine is a {@link Path} of that machine's file system: turned into text and back, or handed to the local
 * file system, it would name the local file with the same path.
 */
public final class PlatformPaths {
    private static final String URL_SEPARATOR = "://";

    private PlatformPaths() {
    }

    /**
     * The machine the project's files live on.
     */
    public static Platform platformOf(Project project) {
        return project.getPlatform();
    }

    /**
     * The machine {@code path} belongs to - the local one for a path of the default file system, and for one no
     * connected target owns (an archive's, say).
     */
    public static Platform platformOf(Path path) {
        if (path.getFileSystem() == FileSystems.getDefault()) {
            return Platform.current();
        }
        PlatformTarget target = Application.get().getInstance(PlatformTargetManager.class).findTargetOf(path);
        Platform platform = target != null ? target.getPlatform() : null;
        return platform != null ? platform : Platform.current();
    }

    /** Whether the machine {@code path} belongs to is a Windows one - not necessarily this one. */
    public static boolean isWindows(Path path) {
        return isLocal(path) ? Platform.current().os().isWindows() : platformOf(path).os().isWindows();
    }

    public static boolean isLocal(Path path) {
        return path.getFileSystem() == FileSystems.getDefault();
    }

    /**
     * Whether the files of the machine are reachable now - false while its target is disconnected.
     */
    public static boolean isReachable(Platform platform) {
        return Application.get().getInstance(PlatformTargetManager.class).isReachable(platform);
    }

    /**
     * The folder a project starts in: its base folder on its machine, otherwise the home of that machine.
     */
    public static Path projectPath(Project project) {
        Platform platform = platformOf(project);
        String basePath = project.getBasePath();
        return basePath != null ? platform.fs().getPath(basePath) : platform.user().homePath();
    }

    /**
     * A path typed or clicked while {@code context} is shown - a path of the same machine.
     */
    public static Path parseIn(Path context, String text) {
        return context.getFileSystem().getPath(text);
    }

    /**
     * The text a path is kept as: the bare path for a local one, the url naming its machine otherwise -
     * {@link #fromPersistent} reads both, a bare path being a local one, as before.
     */
    public static String toPersistent(Path path) {
        if (isLocal(path)) {
            return path.toString();
        }
        String url = VirtualFileManager.getInstance().getUrl(path);
        return url != null ? url : path.toString();
    }

    /**
     * The path {@link #toPersistent} kept, null when its machine is not reachable or the text is not a path.
     */
    public static @Nullable Path fromPersistent(String text) {
        try {
            if (!text.contains(URL_SEPARATOR)) {
                return Path.of(text);
            }
            VirtualFileManager manager = VirtualFileManager.getInstance();
            VirtualFile file = manager.findFileByUrl(text);
            if (file == null) {
                file = manager.refreshAndFindFileByUrl(text);
            }
            return file != null ? file.toNioPath() : null;
        }
        catch (Exception e) {
            return null;
        }
    }

    /**
     * The IDE's file for a path of any machine - never the local file with the same path. Blocking for another machine.
     */
    public static @Nullable VirtualFile findVirtualFile(Path path, boolean refresh) {
        VirtualFileManager manager = VirtualFileManager.getInstance();
        return refresh ? manager.refreshAndFindFileByNioPath(path) : manager.findFileByNioPath(path);
    }
}
