package consulo.turtlecommander.vfs;

import consulo.logging.Logger;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quake / Quake 2 {@code .pak} archives. The format is flat: a fixed 12-byte header, raw file data,
 * and at an arbitrary offset a directory.
 *
 * <ul>
 * <li>Header: {@code PACK} magic (4 bytes), directory offset (int32 LE), directory length
 * (int32 LE, a multiple of 64).</li>
 * <li>Each 64-byte directory entry: a 56-byte null-padded ASCII name (forward-slash separated),
 * the file's offset (int32 LE) and its size (int32 LE).</li>
 * </ul>
 *
 * <p>Served from a temp directory of lazily-materialised stubs — except that PAK's directory gives
 * random access, so materialising an entry is a direct seek + read rather than a re-stream. The
 * temp dir is repacked into a fresh PAK on flush, so add / edit / rename / delete all work.
 */
public class PakVirtualFileSystem extends AbstractTempDirVirtualFileSystem {
    private static final Logger LOG = Logger.getInstance(PakVirtualFileSystem.class);

    private static final int HEADER_SIZE = 12;
    private static final long DIRECTORY_ENTRY_SIZE = 64L;
    private static final int NAME_SIZE = 56;
    private static final int COPY_BUFFER = 64 * 1024;

    /**
     * A still-unmaterialised stub: the entry's (offset, size) in the archive plus the stub's size
     * and mtime as created. The stub metadata keeps materialisation invisible to the base class's
     * dirty-tracking fingerprint and detects stubs overwritten or deleted by external writes.
     */
    private record PendingEntry(long offset, long size, long stubSize, FileTime stubMtime) {
    }

    private record PakEntry(String name, long offset, long size) {
    }

    private record WrittenEntry(String name, long offset, int size) {
    }

    private final Map<Path, PendingEntry> myPendingEntries = new ConcurrentHashMap<>();

    public PakVirtualFileSystem(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        super(archivePath, "turtle-pak-", openProgress);
        openTempDir();
    }

    @Override
    protected void extract(Path into) throws IOException {
        VfsOpenProgress progress = takeOpenProgress();
        myPendingEntries.clear();

        try (RandomAccessFile raf = new RandomAccessFile(getArchivePath().toFile(), "r")) {
            List<PakEntry> entries = readDirectory(raf, raf.length());

            for (int index = 0; index < entries.size(); index++) {
                if (progress.isCancelled()) {
                    break;
                }
                PakEntry entry = entries.get(index);
                progress.onEntry(index + 1, entries.size(), entry.name());

                Path entryPath = VfsPaths.resolveEntryPath(into, entry.name());
                if (entryPath == null) {
                    continue;
                }

                try {
                    Path parent = entryPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    // PAK directories can repeat a name; the eager extractor truncated and rewrote,
                    // so the last occurrence wins here too.
                    Files.deleteIfExists(entryPath);
                    VfsPaths.createSparseStub(entryPath, entry.size());

                    myPendingEntries.put(entryPath.normalize(), new PendingEntry(
                        entry.offset(), entry.size(),
                        Files.size(entryPath), Files.getLastModifiedTime(entryPath)));
                }
                catch (Exception e) {
                    LOG.debug("Failed to stub pak entry: " + entry.name(), e);
                }
            }
        }
    }

    /**
     * Reads the bytes for {@code path} straight from the archive (seek + read) and replaces its
     * sparse stub. A no-op when the path isn't pending, or when the stub was overwritten or deleted
     * by an external write.
     */
    @Override
    public synchronized void materialize(Path path) {
        Path normalized = path.normalize();
        PendingEntry pending = myPendingEntries.get(normalized);
        if (pending == null) {
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(getArchivePath().toFile(), "r")) {
            materializeFromRaf(normalized, pending, raf);
        }
        catch (IOException e) {
            LOG.warn("Failed to materialize pak entry " + normalized, e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected synchronized void onRenamed(Path source, Path target) {
        PendingEntry pending = myPendingEntries.remove(source.normalize());
        if (pending != null) {
            myPendingEntries.put(target.normalize(), pending);
        }
    }

    /**
     * Rebuilds the PAK from the temp directory: a 12-byte header, the concatenated file data, then
     * the directory. PAK is flat, so directories are not stored as entries — they are implied by
     * the forward slashes in the names. Offsets and sizes come from the bytes actually written,
     * which keeps the directory correct even if a file changed size since extraction.
     */
    @Override
    protected void repack(Path from) throws IOException {
        materializeAllForRepack();

        List<WrittenEntry> plan = new ArrayList<>();
        List<Path> sources = new ArrayList<>();
        for (VfsPaths.ArchiveEntry archiveEntry : VfsPaths.collectArchiveEntries(from)) {
            BasicFileAttributes attrs = Files.readAttributes(archiveEntry.path(), BasicFileAttributes.class);
            if (attrs.isDirectory()) {
                continue;
            }
            if (archiveEntry.relativeName().getBytes(StandardCharsets.US_ASCII).length > NAME_SIZE) {
                LOG.warn("Skipping pak entry whose name exceeds " + NAME_SIZE + " bytes: "
                    + archiveEntry.relativeName());
                continue;
            }
            plan.add(new WrittenEntry(archiveEntry.relativeName(), 0, 0));
            sources.add(archiveEntry.path());
        }

        VfsPaths.repackAtomically(getArchivePath(), target -> {
            try (RandomAccessFile raf = new RandomAccessFile(target.toFile(), "rw")) {
                raf.setLength(0);
                raf.seek(HEADER_SIZE);

                List<WrittenEntry> directory = new ArrayList<>(plan.size());
                byte[] buffer = new byte[COPY_BUFFER];

                for (int i = 0; i < plan.size(); i++) {
                    long offset = raf.getFilePointer();
                    long size = 0;
                    try (InputStream input = Files.newInputStream(sources.get(i))) {
                        int read;
                        while ((read = input.read(buffer)) >= 0) {
                            raf.write(buffer, 0, read);
                            size += read;
                        }
                    }
                    directory.add(new WrittenEntry(plan.get(i).name(), offset, (int)size));
                }

                long dirOffset = raf.getFilePointer();
                for (WrittenEntry entry : directory) {
                    byte[] nameField = new byte[NAME_SIZE];
                    byte[] nameBytes = entry.name().getBytes(StandardCharsets.US_ASCII);
                    System.arraycopy(nameBytes, 0, nameField, 0, nameBytes.length);
                    raf.write(nameField);
                    raf.write(leInt32((int)entry.offset()));
                    raf.write(leInt32(entry.size()));
                }

                raf.seek(0);
                raf.write("PACK".getBytes(StandardCharsets.US_ASCII));
                raf.write(leInt32((int)dirOffset));
                raf.write(leInt32((int)(directory.size() * DIRECTORY_ENTRY_SIZE)));
            }
        });
    }

    private void materializeFromRaf(Path path, PendingEntry pending, RandomAccessFile raf) throws IOException {
        long currentSize;
        FileTime currentMtime;
        try {
            currentSize = Files.size(path);
            currentMtime = Files.getLastModifiedTime(path);
        }
        catch (IOException e) {
            // Stub gone: whatever is there now is the user's doing.
            myPendingEntries.remove(path);
            return;
        }

        if (currentSize != pending.stubSize() || !currentMtime.equals(pending.stubMtime())) {
            myPendingEntries.remove(path);
            return;
        }

        Path tmp = Files.createTempFile(path.getParent(), ".tc-materializing-", ".tmp");
        try {
            raf.seek(pending.offset());
            try (OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buffer = new byte[COPY_BUFFER];
                long remaining = pending.size();
                while (remaining > 0) {
                    int toRead = (int)Math.min(remaining, buffer.length);
                    int read = raf.read(buffer, 0, toRead);
                    if (read <= 0) {
                        break;
                    }
                    out.write(buffer, 0, read);
                    remaining -= read;
                }
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        finally {
            Files.deleteIfExists(tmp);
        }

        // Exactly the stub's mtime, so materialisation stays invisible to the dirty-tracking
        // fingerprint.
        Files.setLastModifiedTime(path, pending.stubMtime());
        myPendingEntries.remove(path);
    }

    /**
     * Fills every still-pending stub over one shared archive handle before repack overwrites the
     * archive they read from. Entries whose bytes can't be read are dropped from the temp dir
     * instead of repacking zero-filled stubs.
     */
    private synchronized void materializeAllForRepack() {
        if (myPendingEntries.isEmpty()) {
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(getArchivePath().toFile(), "r")) {
            for (Map.Entry<Path, PendingEntry> pending : new ArrayList<>(myPendingEntries.entrySet())) {
                try {
                    materializeFromRaf(pending.getKey(), pending.getValue(), raf);
                }
                catch (Exception e) {
                    LOG.warn("Dropping unreadable pak entry from repack: " + pending.getKey(), e);
                    dropStub(pending.getKey());
                }
            }
        }
        catch (IOException e) {
            LOG.warn("Bulk pak materialisation failed for " + getArchivePath(), e);
        }

        for (Path path : new ArrayList<>(myPendingEntries.keySet())) {
            LOG.warn("Dropping unreachable pak entry from repack: " + path);
            dropStub(path);
        }
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

    /**
     * Parses the header and directory. Throws for anything that is not a well-formed PAK (bad
     * magic, out-of-bounds directory) so the provider can fall back to the ZIP reader. Individual
     * entries pointing outside the file are skipped rather than aborting the whole archive.
     */
    private static List<PakEntry> readDirectory(RandomAccessFile raf, long fileLength) throws IOException {
        if (fileLength < HEADER_SIZE) {
            throw new IOException("File too small to be a PAK");
        }

        byte[] header = new byte[HEADER_SIZE];
        raf.seek(0);
        raf.readFully(header);

        ByteBuffer hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        if (hb.get() != 'P' || hb.get() != 'A' || hb.get() != 'C' || hb.get() != 'K') {
            throw new IOException("Not a PAK file: missing 'PACK' magic");
        }

        long dirOffset = hb.getInt() & 0xFFFFFFFFL;
        long dirLength = hb.getInt() & 0xFFFFFFFFL;
        if (dirLength % DIRECTORY_ENTRY_SIZE != 0L) {
            throw new IOException("Invalid PAK directory length: " + dirLength);
        }
        if (dirOffset < HEADER_SIZE || dirOffset + dirLength > fileLength) {
            throw new IOException("PAK directory out of bounds");
        }

        int count = (int)(dirLength / DIRECTORY_ENTRY_SIZE);
        if (count == 0) {
            return List.of();
        }

        byte[] dirBytes = new byte[(int)dirLength];
        raf.seek(dirOffset);
        raf.readFully(dirBytes);
        ByteBuffer db = ByteBuffer.wrap(dirBytes).order(ByteOrder.LITTLE_ENDIAN);

        List<PakEntry> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            db.position(i * (int)DIRECTORY_ENTRY_SIZE);
            byte[] nameBytes = new byte[NAME_SIZE];
            db.get(nameBytes);
            long offset = db.getInt() & 0xFFFFFFFFL;
            long size = db.getInt() & 0xFFFFFFFFL;

            String name = decodeName(nameBytes);
            if (name.isEmpty()) {
                continue;
            }
            if (offset + size > fileLength) {
                LOG.debug("Skipping out-of-bounds pak entry: " + name);
                continue;
            }
            result.add(new PakEntry(name, offset, size));
        }
        return result;
    }

    private static String decodeName(byte[] nameBytes) {
        int end = NAME_SIZE;
        for (int i = 0; i < nameBytes.length; i++) {
            if (nameBytes[i] == 0) {
                end = i;
                break;
            }
        }
        return new String(nameBytes, 0, end, StandardCharsets.US_ASCII).trim().replace('\\', '/');
    }

    private static byte[] leInt32(int value) {
        return new byte[]{
            (byte)(value & 0xFF),
            (byte)((value >>> 8) & 0xFF),
            (byte)((value >>> 16) & 0xFF),
            (byte)((value >>> 24) & 0xFF)
        };
    }
}
