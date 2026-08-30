package consulo.turtlecommander.vfs;

import consulo.logging.Logger;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.util.PosixModes;
import consulo.util.lang.StringUtil;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ZIP browsing through Java's built-in zip {@link FileSystem}, which reads and writes entries in
 * place without extracting.
 */
public class ZipVirtualFileSystem implements VirtualFileSystem {
    private static final Logger LOG = Logger.getInstance(ZipVirtualFileSystem.class);

    private final Path myArchivePath;

    private FileSystem myFileSystem;

    /**
     * Unix permission strings parsed from the ZIP central directory, keyed by entry name (no
     * trailing slash). Java's zip {@link FileSystem} doesn't expose the stored Unix mode, so it is
     * read once with Commons Compress and cached — lazily, because most browsing never touches
     * permissions and a fat JAR's central directory is large. Invalidated whenever the archive is
     * rewritten or an entry is renamed. ZIP stores no owner/group names, so only permissions are
     * populated.
     */
    private @Nullable Map<String, ArchiveEntryMetadata> myMetadataCache;

    public ZipVirtualFileSystem(Path archivePath) throws IOException {
        myArchivePath = archivePath;
        myFileSystem = openZipFs();
    }

    @Override
    public Path getArchivePath() {
        return myArchivePath;
    }

    @Override
    public Path getRoot() {
        return myFileSystem.getPath("/");
    }

    @Override
    public boolean isRoot(Path path) {
        String pathStr = path.toString();
        return pathStr.equals("/") || pathStr.isEmpty();
    }

    @Override
    public Path getPath(String relativePath) {
        return myFileSystem.getPath(relativePath);
    }

    @Override
    public List<FileEntry> listFiles(Path directory) throws IOException {
        List<FileEntry> result = new ArrayList<>();

        if (isRoot(directory)) {
            Path archiveParent = myArchivePath.getParent();
            result.add(VfsPaths.parentEntry(archiveParent != null ? archiveParent : myArchivePath));
        }
        else {
            Path parent = directory.getParent();
            result.add(VfsPaths.parentEntry(parent != null ? parent : getRoot()));
        }

        result.addAll(VfsPaths.readDirectoryEntries(directory, this::entryMetadata));
        return result;
    }

    @Override
    public @Nullable ArchiveEntryMetadata entryMetadata(Path path) {
        String key = StringUtil.trimTrailing(StringUtil.trimLeading(path.toString(), '/'), '/');
        if (key.isEmpty()) {
            return null;
        }
        return metadata().get(key);
    }

    @Override
    public Path renameFile(Path source, String newName) throws IOException {
        Path root = myFileSystem.getPath("/");
        Path oldParent = source.getParent();
        if (oldParent == null) {
            oldParent = root;
        }

        Path target = oldParent.resolve(newName);
        // Files.move on the zip filesystem throws FileAlreadyExistsException with no message, which
        // surfaces as a bare class name in the UI. Detect the collision here to give a real error.
        if (!target.equals(source) && Files.exists(target)) {
            throw new FileAlreadyExistsException("Cannot rename to \"" + newName + "\": entry already exists in archive");
        }

        String relativePath = oldParent.equals(root) ? "" : root.relativize(oldParent).toString();
        Files.move(source, target);

        myMetadataCache = null;
        myFileSystem.close();
        myFileSystem = openZipFs();

        Path newParent = relativePath.isEmpty() ? myFileSystem.getPath("/") : myFileSystem.getPath(relativePath);
        return newParent.resolve(newName);
    }

    @Override
    public void flush() throws IOException {
        myMetadataCache = null;
        myFileSystem.close();
        myFileSystem = openZipFs();
    }

    @Override
    public void close() {
        try {
            myFileSystem.close();
        }
        catch (Exception e) {
            LOG.debug("Failed to close zip filesystem for " + myArchivePath, e);
        }
    }

    private FileSystem openZipFs() throws IOException {
        return FileSystems.newFileSystem(myArchivePath, Map.of("create", "false"));
    }

    private Map<String, ArchiveEntryMetadata> metadata() {
        Map<String, ArchiveEntryMetadata> cached = myMetadataCache;
        if (cached == null) {
            cached = buildMetadata();
            myMetadataCache = cached;
        }
        return cached;
    }

    private Map<String, ArchiveEntryMetadata> buildMetadata() {
        Map<String, ArchiveEntryMetadata> map = new HashMap<>();
        try (ZipFile zip = ZipFile.builder().setPath(myArchivePath).get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                int mode = entry.getUnixMode();
                if (mode == 0) {
                    // No external Unix attributes (e.g. a ZIP written on Windows).
                    continue;
                }
                String name = StringUtil.trimTrailing(entry.getName(), '/');
                if (!name.isEmpty()) {
                    map.put(name, ArchiveEntryMetadata.ofPermissions(PosixModes.format(mode)));
                }
            }
        }
        catch (Exception e) {
            LOG.debug("Failed to read zip permissions for " + myArchivePath, e);
        }
        return map;
    }


}
