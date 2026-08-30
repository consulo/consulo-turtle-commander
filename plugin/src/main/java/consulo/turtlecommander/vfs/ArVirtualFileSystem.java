package consulo.turtlecommander.vfs;

import consulo.logging.Logger;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ArVirtualFileSystem extends AbstractTempDirVirtualFileSystem {
    private static final Logger LOG = Logger.getInstance(ArVirtualFileSystem.class);

    /** Default for newly added entries with no recorded metadata: 0100644 (rw-r--r--). */
    private static final int DEFAULT_FILE_MODE = 0x81A4;

    private record ArEntryMetadata(int mode, int userId, int groupId, long lastModifiedSeconds) {
    }

    /**
     * Metadata captured at extract time, keyed by the original entry name. Repack uses it to
     * preserve mode/uid/gid/mtime rather than resetting every entry to 0644/root/root; entries
     * created after extraction fall back to the defaults.
     */
    private final Map<String, ArEntryMetadata> myEntryMetadata = new HashMap<>();

    public ArVirtualFileSystem(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        super(archivePath, "turtle-ar-", openProgress);
        openTempDir();
    }

    @Override
    protected void extract(Path into) throws IOException {
        VfsOpenProgress progress = takeOpenProgress();
        myEntryMetadata.clear();

        try (InputStream raw = Files.newInputStream(getArchivePath());
             ArArchiveInputStream ar = new ArArchiveInputStream(raw)) {

            ArArchiveEntry entry = ar.getNextEntry();
            while (entry != null) {
                if (progress.isCancelled()) {
                    break;
                }
                progress.onEntry(0, 0, entry.getName());

                Path entryPath = VfsPaths.resolveEntryPath(into, entry.getName());
                if (entryPath == null) {
                    entry = ar.getNextEntry();
                    continue;
                }

                Path parent = entryPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(ar, entryPath);

                myEntryMetadata.put(entry.getName(), new ArEntryMetadata(
                    entry.getMode(), entry.getUserId(), entry.getGroupId(), entry.getLastModified()));

                try {
                    Date lastModified = entry.getLastModifiedDate();
                    if (lastModified != null) {
                        Files.setLastModifiedTime(entryPath, FileTime.fromMillis(lastModified.getTime()));
                    }
                }
                catch (Exception e) {
                    LOG.debug("Failed to set modified time for ar entry: " + entry.getName(), e);
                }

                entry = ar.getNextEntry();
            }
        }
    }

    @Override
    protected void onRenamed(Path source, Path target) {
        // Carry metadata over so repack keeps the original mode/uid/gid/mtime.
        ArEntryMetadata metadata = myEntryMetadata.remove(relativeName(source));
        if (metadata != null) {
            myEntryMetadata.put(relativeName(target), metadata);
        }
    }

    @Override
    protected void repack(Path from) throws IOException {
        VfsPaths.repackAtomically(getArchivePath(), target -> {
            try (OutputStream raw = Files.newOutputStream(target);
                 ArArchiveOutputStream ar = new ArArchiveOutputStream(raw)) {

                for (VfsPaths.ArchiveEntry archiveEntry : VfsPaths.collectArchiveEntries(from)) {
                    try {
                        BasicFileAttributes attrs =
                            Files.readAttributes(archiveEntry.path(), BasicFileAttributes.class);
                        if (attrs.isDirectory()) {
                            // The ar format is flat; there are no directory entries.
                            continue;
                        }

                        ArEntryMetadata metadata = myEntryMetadata.get(archiveEntry.relativeName());
                        ArArchiveEntry entry = new ArArchiveEntry(
                            archiveEntry.relativeName(),
                            attrs.size(),
                            metadata != null ? metadata.userId() : 0,
                            metadata != null ? metadata.groupId() : 0,
                            metadata != null ? metadata.mode() : DEFAULT_FILE_MODE,
                            metadata != null ? metadata.lastModifiedSeconds() : attrs.lastModifiedTime().toMillis() / 1000
                        );

                        ar.putArchiveEntry(entry);
                        Files.copy(archiveEntry.path(), ar);
                        ar.closeArchiveEntry();
                    }
                    catch (IOException e) {
                        LOG.warn("Failed to repack entry: " + archiveEntry.relativeName(), e);
                    }
                }
            }
        });
    }

    private String relativeName(Path path) {
        return getTempDir().relativize(path).toString().replace('\\', '/');
    }
}
