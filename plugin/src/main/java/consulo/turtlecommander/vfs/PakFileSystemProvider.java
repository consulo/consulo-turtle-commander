package consulo.turtlecommander.vfs;

import consulo.logging.Logger;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public class PakFileSystemProvider implements VirtualFileSystemProvider {
    private static final Logger LOG = Logger.getInstance(PakFileSystemProvider.class);

    public static final Set<String> ARCHIVE_EXTENSIONS = Set.of("pak");

    @Override
    public boolean supportsExtension(String extension) {
        return ARCHIVE_EXTENSIONS.contains(extension);
    }

    @Override
    public VirtualFileSystem create(Path archivePath) throws IOException {
        return create(archivePath, null);
    }

    /**
     * Opens a {@code .pak} as the first format that parses: a Quake / Quake 2 PAK archive
     * ({@code PACK} magic), then ZIP — some games and mods ship {@code .pak} files that are really
     * ZIPs. If neither parses, {@link SilentVfsOpenException} leaves the file as it is without
     * surfacing an error.
     */
    @Override
    public VirtualFileSystem create(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        try {
            return new PakVirtualFileSystem(archivePath, openProgress);
        }
        catch (Exception e) {
            LOG.debug("Not a Quake PAK, trying ZIP fallback: " + archivePath, e);
        }

        try {
            return new ZipFileSystemProvider().create(archivePath, openProgress);
        }
        catch (Exception e) {
            LOG.debug("Not a ZIP either, leaving file as-is: " + archivePath, e);
            throw new SilentVfsOpenException();
        }
    }
}
