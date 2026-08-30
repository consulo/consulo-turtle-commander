package consulo.turtlecommander.vfs;

import consulo.util.io.FileUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public interface VirtualFileSystemProvider {
    boolean supportsExtension(String extension);

    VirtualFileSystem create(Path archivePath) throws IOException;

    /**
     * Extracts the lowercased extension from the file name and delegates to
     * {@link #supportsExtension}, confirming the path points to a regular file.
     */
    default boolean supports(Path path) {
        String extension = extensionOf(path);
        return !extension.isEmpty() && supportsExtension(extension) && Files.isRegularFile(path);
    }

    /**
     * Create with an extract-progress hook. The default ignores it — only implementations that do
     * significant work at open time (the ZIP extract fallback and ISO) override this.
     */
    default VirtualFileSystem create(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        return create(archivePath);
    }

    static String extensionOf(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return "";
        }
        return extensionOf(fileName.toString());
    }

    static String extensionOf(String fileName) {
        return FileUtil.getExtension(fileName).toLowerCase(Locale.ROOT);
    }
}
