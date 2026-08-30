package consulo.turtlecommander.vfs;

import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A browsable archive mounted as a filesystem.
 *
 * <p>{@link #listFiles} and {@link #renameFile} block on I/O and must be called off the UI thread.
 */
public interface VirtualFileSystem extends Closeable {
    Path getArchivePath();

    Path getRoot();

    default boolean isReadOnly() {
        return false;
    }

    List<FileEntry> listFiles(Path directory) throws IOException;

    boolean isRoot(Path path);

    Path getPath(String relativePath);

    /** Writes pending temp-dir changes back into the archive. Failures must reach the caller: a
     *  swallowed repack error silently loses the user's edit. */
    void flush() throws IOException;

    Path renameFile(Path source, String newName) throws IOException;

    /**
     * Whether {@code path} was produced by this VFS — used by {@link OpenVfsRegistry} to dispatch a
     * materialization call to the right instance. The default prefix-matches against
     * {@link #getRoot()}, which is correct for every temp-dir-backed implementation.
     */
    default boolean owns(Path path) {
        return path.normalize().startsWith(getRoot().normalize());
    }

    /**
     * Lazy-VFS hook: ensure the bytes for {@code path} are actually present on disk. The default is
     * a no-op because most implementations extract everything up-front and the temp-dir copies are
     * already real files. The ISO filesystem overrides this to stream content from the source disc
     * image only when something needs it, so opening a 5 GB ISO doesn't pre-extract 5 GB.
     */
    default void materialize(Path path) {
    }

    /**
     * Owner / group / permission metadata stored in the archive for the entry at {@code path}, or
     * null when this VFS carries none — the temp-dir copy's own stat is meaningless, since
     * extraction gives it the host umask rather than the archive's recorded mode. Formats that do
     * capture it (tar, zip) override this so listings, and the colorization rules that match on
     * owner/group/permissions, see the real values.
     */
    default @Nullable ArchiveEntryMetadata entryMetadata(Path path) {
        return null;
    }
}
