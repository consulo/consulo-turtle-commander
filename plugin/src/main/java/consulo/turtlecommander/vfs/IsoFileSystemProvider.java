package consulo.turtlecommander.vfs;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public class IsoFileSystemProvider implements VirtualFileSystemProvider {
    public static final Set<String> ARCHIVE_EXTENSIONS = Set.of("iso");

    @Override
    public boolean supportsExtension(String extension) {
        return ARCHIVE_EXTENSIONS.contains(extension);
    }

    @Override
    public VirtualFileSystem create(Path archivePath) throws IOException {
        return create(archivePath, null);
    }

    @Override
    public VirtualFileSystem create(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        return new IsoVirtualFileSystem(archivePath, openProgress);
    }
}
