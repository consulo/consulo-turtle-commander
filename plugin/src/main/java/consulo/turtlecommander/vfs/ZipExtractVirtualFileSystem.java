package consulo.turtlecommander.vfs;

import consulo.logging.Logger;
import consulo.turtlecommander.util.PosixModes;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback ZIP filesystem using Commons Compress, for archives that Java's built-in zip
 * {@link java.nio.file.FileSystem} rejects (e.g. "Invalid CEN header").
 *
 * <p>Content is materialised lazily: at open time only the directory structure is written, with
 * each file represented by a sparse stub of the correct size and timestamp. Real bytes stream from
 * the archive when something reads them, via {@link OpenVfsRegistry#materializeIfNeeded}. Unlike
 * the ISO filesystem this one is writable, which adds a rule: {@link #repack} must materialise
 * every remaining stub first, because the source archive — the stubs' only backing store — is
 * overwritten immediately after.
 */
public class ZipExtractVirtualFileSystem extends AbstractTempDirVirtualFileSystem {
    private static final Logger LOG = Logger.getInstance(ZipExtractVirtualFileSystem.class);

    /**
     * The archive entry backing a still-unmaterialised stub, plus the stub's size and mtime as
     * created. The metadata serves two purposes: {@link #materialize} gives the materialised file
     * the stub's exact mtime (so materialisation never flips the base class's dirty-tracking
     * fingerprint), and it detects a stub overwritten or deleted by an external write — in that
     * case the user's content wins and the pending record is dropped.
     */
    private record PendingEntry(ZipArchiveEntry entry, long stubSize, FileTime stubMtime) {
    }

    /**
     * Stub path (normalised) to its pending record. Populated during extract and drained by
     * materialize; once a path leaves the map the stub holds real (or user-written) content.
     * Concurrent because content search and parallel hash actions can race.
     */
    private final Map<Path, PendingEntry> myPendingEntries = new ConcurrentHashMap<>();

    /**
     * Unix permission strings from each entry's external attributes, keyed by temp-dir-relative
     * name. Surfaced in listings because the extracted stub only carries the host umask.
     */
    private final Map<String, ArchiveEntryMetadata> myEntryMetadataByName = new ConcurrentHashMap<>();

    /**
     * Kept open for the filesystem's lifetime so materialize can stream individual entries without
     * re-parsing the central directory each time. Re-created by extract (which runs again on every
     * flush) and closed in close() — and in repack, before the archive is rewritten underneath it.
     */
    private @Nullable ZipFile myZipReader;

    public ZipExtractVirtualFileSystem(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        super(archivePath, "turtle-zip-", openProgress);
        openTempDir();
    }

    @Override
    protected void extract(Path into) throws IOException {
        VfsOpenProgress progress = takeOpenProgress();

        // flush() re-extracts into a fresh temp dir: drop the previous round's reader and stub
        // bookkeeping first.
        closeReader();
        myPendingEntries.clear();
        myEntryMetadataByName.clear();

        ZipFile zip = ZipFile.builder().setPath(getArchivePath()).get();
        try {
            // Iterating the (already in-memory) central directory twice is cheaper than
            // materialising a list just to know the total for the progress fraction — fat JARs can
            // contain millions of entries.
            int total = 0;
            Enumeration<ZipArchiveEntry> counter = zip.getEntries();
            while (counter.hasMoreElements()) {
                counter.nextElement();
                total++;
            }

            int index = 0;
            Enumeration<ZipArchiveEntry> iter = zip.getEntries();
            while (iter.hasMoreElements()) {
                ZipArchiveEntry entry = iter.nextElement();
                if (progress.isCancelled()) {
                    break;
                }
                index++;
                progress.onEntry(index, total, entry.getName());

                Path entryPath = VfsPaths.resolveEntryPath(into, entry.getName());
                if (entryPath == null) {
                    continue;
                }

                int mode = entry.getUnixMode();
                if (mode != 0) {
                    myEntryMetadataByName.put(relativeName(into, entryPath),
                        ArchiveEntryMetadata.ofPermissions(PosixModes.format(mode)));
                }

                try {
                    stubEntry(entry, entryPath);
                }
                catch (Exception e) {
                    LOG.debug("Failed to stub zip entry: " + entry.getName(), e);
                }
            }
        }
        catch (Throwable e) {
            try {
                zip.close();
            }
            catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }

        myZipReader = zip;
    }

    /**
     * Streams the actual bytes for {@code path} and replaces its sparse stub. A no-op when the path
     * isn't one of ours or is already materialised — and a deliberate no-op (record dropped) when
     * the stub was overwritten or deleted by an external write, so a copy-into-archive or a delete
     * is never clobbered or resurrected. Synchronised so two consumers reading the same file
     * concurrently each get the full content rather than racing through the copy.
     */
    @Override
    public synchronized void materialize(Path path) {
        Path normalized = path.normalize();
        PendingEntry pending = myPendingEntries.get(normalized);
        if (pending == null) {
            return;
        }
        ZipFile zip = myZipReader;
        if (zip == null) {
            return;
        }

        long currentSize;
        FileTime currentMtime;
        try {
            currentSize = Files.size(normalized);
            currentMtime = Files.getLastModifiedTime(normalized);
        }
        catch (IOException e) {
            // Stub gone: whatever is (or isn't) there now is the user's doing, not ours to restore.
            myPendingEntries.remove(normalized);
            return;
        }

        if (currentSize != pending.stubSize() || !currentMtime.equals(pending.stubMtime())) {
            // Replaced in place — same reasoning as above.
            myPendingEntries.remove(normalized);
            return;
        }

        try {
            // Write into a sibling temp file then atomic-move, so a partial write never leaves a
            // half-streamed file at the canonical path.
            Path tmp = Files.createTempFile(normalized.getParent(), ".tc-materializing-", ".tmp");
            try {
                try (InputStream input = zip.getInputStream(pending.entry())) {
                    Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(tmp, normalized, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            finally {
                Files.deleteIfExists(tmp);
            }
            // Exactly the stub's mtime, so materialisation stays invisible to the base class's
            // dirty-tracking fingerprint.
            Files.setLastModifiedTime(normalized, pending.stubMtime());
            myPendingEntries.remove(normalized);
        }
        catch (IOException e) {
            LOG.warn("Failed to materialize zip entry " + normalized, e);
            throw new java.io.UncheckedIOException(e);
        }
    }

    /**
     * The base class's rename moves the stub on disk; keep the pending map keyed by the stub's new
     * location so materialize (and repack) stream the bytes to where it actually lives now.
     */
    @Override
    protected synchronized void onRenamed(Path source, Path target) {
        PendingEntry pending = myPendingEntries.remove(source.normalize());
        if (pending != null) {
            myPendingEntries.put(target.normalize(), pending);
        }

        ArchiveEntryMetadata metadata = myEntryMetadataByName.remove(relativeName(getTempDir(), source));
        if (metadata != null) {
            myEntryMetadataByName.put(relativeName(getTempDir(), target), metadata);
        }
    }

    @Override
    public @Nullable ArchiveEntryMetadata entryMetadata(Path path) {
        return myEntryMetadataByName.get(relativeName(getTempDir(), path));
    }

    @Override
    protected void repack(Path from) throws IOException {
        materializeAllForRepack();
        // Release the read handle: the atomic move below replaces the same file.
        closeReader();

        VfsPaths.repackAtomically(getArchivePath(), target -> {
            try (OutputStream out = Files.newOutputStream(target);
                 ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
                for (VfsPaths.ArchiveEntry archiveEntry : VfsPaths.collectArchiveEntries(from)) {
                    try {
                        BasicFileAttributes attrs =
                            Files.readAttributes(archiveEntry.path(), BasicFileAttributes.class);
                        String entryName = attrs.isDirectory()
                            ? archiveEntry.relativeName() + "/"
                            : archiveEntry.relativeName();

                        ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
                        entry.setTime(attrs.lastModifiedTime().toMillis());
                        if (!attrs.isDirectory()) {
                            entry.setSize(attrs.size());
                        }
                        zip.putArchiveEntry(entry);
                        if (!attrs.isDirectory()) {
                            Files.copy(archiveEntry.path(), zip);
                        }
                        zip.closeArchiveEntry();
                    }
                    catch (IOException e) {
                        LOG.warn("Failed to repack entry: " + archiveEntry.relativeName(), e);
                    }
                }
            }
        });
    }

    @Override
    public void close() {
        closeReader();
        myPendingEntries.clear();
        super.close();
    }

    private void stubEntry(ZipArchiveEntry entry, Path entryPath) throws IOException {
        Date lastModified = entry.getLastModifiedDate();

        if (entry.isDirectory()) {
            Files.createDirectories(entryPath);
            if (lastModified != null) {
                Files.setLastModifiedTime(entryPath, FileTime.fromMillis(lastModified.getTime()));
            }
            return;
        }

        Path parent = entryPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        VfsPaths.createSparseStub(entryPath, entry.getSize());
        if (lastModified != null) {
            Files.setLastModifiedTime(entryPath, FileTime.fromMillis(lastModified.getTime()));
        }

        myPendingEntries.put(entryPath.normalize(),
            new PendingEntry(entry, Files.size(entryPath), Files.getLastModifiedTime(entryPath)));
    }

    /**
     * Materialises every still-pending stub before repack overwrites the archive they stream from.
     * An entry whose bytes can't be read is dropped from the temp dir — the same outcome the eager
     * extractor produced when an entry failed — rather than repacking a zero-filled stub.
     */
    private synchronized void materializeAllForRepack() {
        for (Path path : new ArrayList<>(myPendingEntries.keySet())) {
            try {
                materialize(path);
            }
            catch (Exception e) {
                LOG.warn("Dropping unreadable zip entry from repack: " + path, e);
                try {
                    Files.deleteIfExists(path);
                }
                catch (IOException ignored) {
                    // Best effort: the entry stays as a zero-filled stub in the repacked archive.
                }
                myPendingEntries.remove(path);
            }
        }
    }

    private void closeReader() {
        ZipFile reader = myZipReader;
        if (reader != null) {
            try {
                reader.close();
            }
            catch (Exception e) {
                LOG.debug("Failed to close zip reader for " + getArchivePath(), e);
            }
        }
        myZipReader = null;
    }

    private static String relativeName(Path base, Path path) {
        return base.relativize(path).toString().replace('\\', '/');
    }
}
