package consulo.turtlecommander.vfs;

import consulo.logging.Logger;
import consulo.turtlecommander.util.PosixModes;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filesystem for tar archives — plain, and (through the stream factories supplied by the
 * Gz/Bz2/Xz providers) the compressed {@code .tar.gz} / {@code .tar.bz2} / {@code .tar.xz}
 * variants.
 *
 * <p>Content materialises lazily: opening streams the entry headers once and writes only sparse
 * size-correct stubs. Tar has no random-access index, so a single {@link #materialize} re-streams
 * the archive up to the entry — identified by its ordinal, which is immune to duplicate names — and
 * the bulk pass fills every remaining stub at once before {@link #repack} overwrites the archive.
 *
 * <p>Symlinks and hard links are resolved eagerly at open: they carry no body, and a hard link
 * created against a stub inode would keep pointing at the zeros after the target's atomic
 * replacement.
 */
public class TarVirtualFileSystem extends AbstractTempDirVirtualFileSystem {
    private static final Logger LOG = Logger.getInstance(TarVirtualFileSystem.class);

    @FunctionalInterface
    public interface InputStreamFactory {
        InputStream open(Path path) throws IOException;
    }

    @FunctionalInterface
    public interface OutputStreamFactory {
        OutputStream open(Path path) throws IOException;
    }

    /**
     * A still-unmaterialised stub: the ordinal of its entry in the archive stream plus the stub's
     * size and mtime as created. The metadata keeps materialisation invisible to the base class's
     * dirty-tracking fingerprint and detects stubs overwritten or deleted by external writes.
     */
    private record PendingEntry(int ordinal, long stubSize, FileTime stubMtime) {
    }

    private final InputStreamFactory myInputStreamFactory;
    private final @Nullable OutputStreamFactory myOutputStreamFactory;

    /**
     * Symlink entries that could not be created on the local filesystem (typically Windows without
     * SeCreateSymbolicLinkPrivilege), keyed by entry name so repack can re-emit them even though
     * the temp dir has nothing to walk. Value is the stored link target.
     */
    private final Map<String, String> myUnresolvedSymlinks = new LinkedHashMap<>();

    private final Map<Path, PendingEntry> myPendingEntries = new ConcurrentHashMap<>();

    /**
     * Owner / group / permissions from the tar header, keyed by temp-dir-relative name. Tar carries
     * a full Unix mode plus owner and group names, so all three are surfaced — the extracted copy
     * itself only has the host umask, which would be meaningless.
     */
    private final Map<String, ArchiveEntryMetadata> myEntryMetadataByName = new HashMap<>();

    public TarVirtualFileSystem(Path archivePath, InputStreamFactory inputStreamFactory,
                                @Nullable OutputStreamFactory outputStreamFactory,
                                @Nullable VfsOpenProgress openProgress) throws IOException {
        super(archivePath, "turtle-tar-", openProgress);
        myInputStreamFactory = inputStreamFactory;
        myOutputStreamFactory = outputStreamFactory;
        openTempDir();
    }

    @Override
    protected void extract(Path into) throws IOException {
        VfsOpenProgress progress = takeOpenProgress();
        myUnresolvedSymlinks.clear();
        myPendingEntries.clear();
        myEntryMetadataByName.clear();

        try (InputStream raw = myInputStreamFactory.open(getArchivePath());
             TarArchiveInputStream tar = new TarArchiveInputStream(raw)) {
            int ordinal = -1;
            TarArchiveEntry entry = tar.getNextEntry();
            while (entry != null) {
                ordinal++;
                if (progress.isCancelled()) {
                    break;
                }
                progress.onEntry(0, 0, entry.getName());

                Path entryPath = VfsPaths.resolveEntryPath(into, entry.getName());
                if (entryPath == null) {
                    entry = tar.getNextEntry();
                    continue;
                }

                myEntryMetadataByName.put(relativeName(into, entryPath), new ArchiveEntryMetadata(
                    entry.getUserName() != null ? entry.getUserName() : "",
                    entry.getGroupName() != null ? entry.getGroupName() : "",
                    PosixModes.format(entry.getMode())
                ));

                try {
                    extractEntry(into, entry, entryPath, ordinal);
                }
                catch (Exception e) {
                    LOG.debug("Failed to stub tar entry: " + entry.getName(), e);
                }

                entry = tar.getNextEntry();
            }
        }
    }

    private void extractEntry(Path into, TarArchiveEntry entry, Path entryPath, int ordinal) throws IOException {
        if (entry.isSymbolicLink()) {
            String linkTarget = entry.getLinkName();
            if (VfsPaths.symlinkTargetEscapes(into, entryPath, linkTarget)) {
                // Refuse a link pointing outside the extraction root: creating it would let a later
                // child entry (or a copy-out) write through it into the host filesystem.
                LOG.warn("Skipping tar symlink escaping the archive root: " + entry.getName() + " -> " + linkTarget);
                return;
            }
            createParent(entryPath);
            try {
                Files.createSymbolicLink(entryPath, Path.of(linkTarget));
            }
            catch (Exception e) {
                // May fail (e.g. Windows without privileges). Record it so repack can re-emit the
                // original entry and users don't silently lose symlinks when they edit a tar.
                myUnresolvedSymlinks.put(relativeName(into, entryPath), linkTarget);
            }
            setEntryMtime(entryPath, entry);
            return;
        }

        if (entry.isDirectory()) {
            Files.createDirectories(entryPath);
            setEntryMtime(entryPath, entry);
            return;
        }

        if (entry.isLink()) {
            // Hard link: the target precedes the link in any well-formed tar, so it is already
            // recorded — materialise it now and link against the real inode. The target goes
            // through resolveEntryPath so a malicious linkName like "/etc/passwd" or "../../secret"
            // (which would hard-link or copy a host file into the archive) is rejected.
            Path linkTarget = VfsPaths.resolveEntryPath(into, entry.getLinkName());
            if (linkTarget == null) {
                LOG.warn("Skipping tar hard link escaping the archive root: "
                    + entry.getName() + " -> " + entry.getLinkName());
                return;
            }
            createParent(entryPath);
            materialize(linkTarget);
            try {
                Files.createLink(entryPath, linkTarget);
            }
            catch (Exception e) {
                if (Files.exists(linkTarget)) {
                    Files.copy(linkTarget, entryPath);
                }
            }
            setEntryMtime(entryPath, entry);
            return;
        }

        createParent(entryPath);
        VfsPaths.createSparseStub(entryPath, entry.getSize());
        setEntryMtime(entryPath, entry);
        myPendingEntries.put(entryPath.normalize(),
            new PendingEntry(ordinal, Files.size(entryPath), Files.getLastModifiedTime(entryPath)));
    }

    /**
     * Streams the bytes for {@code path} by walking the archive up to the recorded ordinal. Tar has
     * no index, so this costs one pass (decompressing it for the {@code .tar.gz} family); the
     * open-time saving still wins unless every entry is read.
     */
    @Override
    public synchronized void materialize(Path path) {
        Path normalized = path.normalize();
        PendingEntry pending = myPendingEntries.get(normalized);
        if (pending == null) {
            return;
        }

        try {
            try (InputStream raw = myInputStreamFactory.open(getArchivePath());
                 TarArchiveInputStream tar = new TarArchiveInputStream(raw)) {
                int ordinal = -1;
                TarArchiveEntry entry = tar.getNextEntry();
                while (entry != null) {
                    ordinal++;
                    if (ordinal == pending.ordinal()) {
                        materializeFromCurrentEntry(normalized, tar);
                        return;
                    }
                    entry = tar.getNextEntry();
                }
            }
            // Stream ended before the recorded ordinal: the archive shrank underneath us. Drop the
            // record so a repack omits the stub instead of writing zeros.
            LOG.warn("Tar entry vanished from " + getArchivePath() + ", dropping stub: " + normalized);
            myPendingEntries.remove(normalized);
        }
        catch (IOException e) {
            LOG.warn("Failed to materialize tar entry " + normalized, e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected synchronized void onRenamed(Path source, Path target) {
        PendingEntry pending = myPendingEntries.remove(source.normalize());
        if (pending != null) {
            myPendingEntries.put(target.normalize(), pending);
        }
        // Carry display metadata over so a renamed entry keeps showing the archive's recorded
        // owner/group/permissions instead of reverting to blanks.
        ArchiveEntryMetadata metadata = myEntryMetadataByName.remove(relativeName(getTempDir(), source));
        if (metadata != null) {
            myEntryMetadataByName.put(relativeName(getTempDir(), target), metadata);
        }
    }

    @Override
    protected void repack(Path from) throws IOException {
        materializeAllForRepack();

        OutputStreamFactory outFactory = myOutputStreamFactory != null ? myOutputStreamFactory : Files::newOutputStream;
        VfsPaths.repackAtomically(getArchivePath(), tmpArchive -> {
            try (OutputStream raw = outFactory.open(tmpArchive);
                 TarArchiveOutputStream tar = new TarArchiveOutputStream(raw)) {
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

                for (VfsPaths.ArchiveEntry archiveEntry : VfsPaths.collectArchiveEntries(from)) {
                    try {
                        writeRepackEntry(tar, archiveEntry.path(), archiveEntry.relativeName());
                    }
                    catch (IOException e) {
                        LOG.warn("Failed to repack entry: " + archiveEntry.relativeName(), e);
                    }
                }

                // Re-emit symlinks we could not materialize locally. Without this, editing any file
                // inside a tar on Windows silently strips every symlink.
                for (Map.Entry<String, String> unresolved : myUnresolvedSymlinks.entrySet()) {
                    TarArchiveEntry entry = new TarArchiveEntry(unresolved.getKey(), TarArchiveEntry.LF_SYMLINK);
                    entry.setLinkName(unresolved.getValue());
                    tar.putArchiveEntry(entry);
                    tar.closeArchiveEntry();
                }
            }
        });
    }

    @Override
    public @Nullable ArchiveEntryMetadata entryMetadata(Path path) {
        return myEntryMetadataByName.get(relativeName(getTempDir(), path));
    }

    private void writeRepackEntry(TarArchiveOutputStream tar, Path path, String relativeName) throws IOException {
        if (Files.isSymbolicLink(path)) {
            Path target = Files.readSymbolicLink(path);
            TarArchiveEntry entry = new TarArchiveEntry(relativeName, TarArchiveEntry.LF_SYMLINK);
            entry.setLinkName(target.toString().replace('\\', '/'));
            tar.putArchiveEntry(entry);
            tar.closeArchiveEntry();
            return;
        }

        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        TarArchiveEntry entry = new TarArchiveEntry(path, relativeName);
        entry.setSize(attrs.isDirectory() ? 0 : attrs.size());
        entry.setModTime(new Date(attrs.lastModifiedTime().toMillis()));
        tar.putArchiveEntry(entry);
        if (!attrs.isDirectory()) {
            Files.copy(path, tar);
        }
        tar.closeArchiveEntry();
    }

    /**
     * Fills every still-pending stub in a single pass before repack overwrites the archive. Entries
     * whose bytes can't be read, or that the stream no longer reaches, are dropped from the temp
     * dir — the same outcome the eager extractor produced for unextractable entries.
     */
    private synchronized void materializeAllForRepack() {
        if (myPendingEntries.isEmpty()) {
            return;
        }

        Map<Integer, Path> byOrdinal = new HashMap<>();
        for (Map.Entry<Path, PendingEntry> pending : myPendingEntries.entrySet()) {
            byOrdinal.put(pending.getValue().ordinal(), pending.getKey());
        }

        try (InputStream raw = myInputStreamFactory.open(getArchivePath());
             TarArchiveInputStream tar = new TarArchiveInputStream(raw)) {
            int ordinal = -1;
            TarArchiveEntry entry = tar.getNextEntry();
            while (entry != null && !byOrdinal.isEmpty()) {
                ordinal++;
                Path path = byOrdinal.remove(ordinal);
                if (path != null) {
                    try {
                        materializeFromCurrentEntry(path, tar);
                    }
                    catch (Exception e) {
                        LOG.warn("Dropping unreadable tar entry from repack: " + path, e);
                        dropStub(path);
                    }
                }
                entry = tar.getNextEntry();
            }
        }
        catch (IOException e) {
            LOG.warn("Bulk tar materialisation failed for " + getArchivePath(), e);
        }

        for (Path path : byOrdinal.values()) {
            LOG.warn("Dropping unreachable tar entry from repack: " + path);
            dropStub(path);
        }
    }

    /**
     * Copies the stream's current entry body onto the stub, with the same guards as the zip
     * implementation: skip (and drop) externally-modified stubs, write via a sibling temp file plus
     * atomic move, and stamp the stub's exact mtime so materialisation never flips the
     * dirty-tracking fingerprint.
     */
    private void materializeFromCurrentEntry(Path path, TarArchiveInputStream tar) throws IOException {
        PendingEntry pending = myPendingEntries.get(path);
        if (pending == null) {
            return;
        }

        long currentSize;
        FileTime currentMtime;
        try {
            currentSize = Files.size(path);
            currentMtime = Files.getLastModifiedTime(path);
        }
        catch (IOException e) {
            myPendingEntries.remove(path);
            return;
        }

        if (currentSize != pending.stubSize() || !currentMtime.equals(pending.stubMtime())) {
            myPendingEntries.remove(path);
            return;
        }

        Path tmp = Files.createTempFile(path.getParent(), ".tc-materializing-", ".tmp");
        try {
            Files.copy(tar, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        finally {
            Files.deleteIfExists(tmp);
        }
        Files.setLastModifiedTime(path, pending.stubMtime());
        myPendingEntries.remove(path);
    }

    private void dropStub(Path path) {
        try {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored) {
            // Best effort: a stub we cannot delete is repacked as-is.
        }
        myPendingEntries.remove(path);
    }

    private static void createParent(Path entryPath) throws IOException {
        Path parent = entryPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static void setEntryMtime(Path path, TarArchiveEntry entry) throws IOException {
        Date lastModified = entry.getLastModifiedDate();
        if (lastModified != null) {
            Files.setLastModifiedTime(path, FileTime.fromMillis(lastModified.getTime()));
        }
    }

    private static String relativeName(Path base, Path path) {
        return base.relativize(path).toString().replace('\\', '/');
    }
}
