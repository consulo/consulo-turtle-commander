package consulo.turtlecommander.vfs;

import consulo.logging.Logger;
import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Shared helpers for the archive filesystems: listing, safe path resolution, and repacking. */
public final class VfsPaths {
    private static final Logger LOG = Logger.getInstance(VfsPaths.class);

    private static final char[] ILLEGAL_FILENAME_CHARS = {'<', '>', ':', '"', '|', '?', '*'};

    private VfsPaths() {
    }

    public static FileEntry parentEntry(Path path) {
        return FileEntry.builder("..", path).directory(true).parentLink(true).build();
    }

    public static List<FileEntry> readDirectoryEntries(Path directory) throws IOException {
        return readDirectoryEntries(directory, path -> null);
    }

    /** Directories first, then files, each sorted case-insensitively by name. */
    public static List<FileEntry> readDirectoryEntries(
        Path directory,
        Function<Path, @Nullable ArchiveEntryMetadata> metadataFor
    ) throws IOException {
        List<FileEntry> dirs = new ArrayList<>();
        List<FileEntry> files = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                    ArchiveEntryMetadata meta = metadataFor.apply(entry);

                    FileEntry fileEntry = FileEntry.builder(entry.getFileName().toString(), entry)
                        .directory(attrs.isDirectory())
                        .size(attrs.isDirectory() ? 0 : attrs.size())
                        .creationTime(attrs.creationTime())
                        .lastModified(attrs.lastModifiedTime())
                        .owner(meta != null ? meta.owner() : "")
                        .group(meta != null ? meta.group() : "")
                        .permissions(meta != null ? meta.permissions() : "")
                        .build();

                    if (attrs.isDirectory()) {
                        dirs.add(fileEntry);
                    }
                    else {
                        files.add(fileEntry);
                    }
                }
                catch (Exception e) {
                    LOG.debug("Failed to read directory entry: " + entry.getFileName(), e);
                }
            }
        }

        Comparator<FileEntry> byName = Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT));
        dirs.sort(byName);
        files.sort(byName);

        List<FileEntry> result = new ArrayList<>(dirs.size() + files.size());
        result.addAll(dirs);
        result.addAll(files);
        return result;
    }

    /** One entry of a deterministic archive walk: the real path plus its repack-relative name. */
    public record ArchiveEntry(Path path, String relativeName) {
    }

    /**
     * Walks {@code tempDir} and returns every descendant in a deterministic order: parents before
     * children (so archive entries with implicit directory entries are well-formed), siblings
     * sorted by repack-relative name. Deterministic traversal makes repack output byte-stable
     * across filesystems, which matters for checksums and CI.
     */
    public static List<ArchiveEntry> collectArchiveEntries(Path tempDir) throws IOException {
        List<Path> collected = new ArrayList<>();
        Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(tempDir)) {
                    collected.add(dir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                collected.add(file);
                return FileVisitResult.CONTINUE;
            }
        });

        List<ArchiveEntry> entries = new ArrayList<>(collected.size());
        for (Path path : collected) {
            entries.add(new ArchiveEntry(path, tempDir.relativize(path).toString().replace('\\', '/')));
        }
        entries.sort(Comparator.comparing(ArchiveEntry::relativeName));
        return entries;
    }

    /**
     * Resolves an archive entry name against a base directory, sanitizing characters that are
     * illegal in file names on the host OS. Returns null if the resolved path escapes the base
     * directory (zip-slip).
     */
    public static @Nullable Path resolveEntryPath(Path baseDir, String entryName) {
        String cleanName = entryName.endsWith("/") ? entryName.substring(0, entryName.length() - 1) : entryName;
        try {
            return containedOrNull(baseDir, baseDir.resolve(cleanName));
        }
        catch (InvalidPathException e) {
            return containedOrNull(baseDir, baseDir.resolve(sanitize(cleanName)));
        }
    }

    /**
     * True when a symbolic link located at {@code linkPath} pointing at {@code target} would
     * resolve outside {@code baseDir} — i.e. following it, or writing through it, escapes the
     * extraction root.
     *
     * <p>{@link #resolveEntryPath} only rejects textual {@code ..} in an entry <em>name</em>; it
     * can't see link <em>targets</em>. Without this check a hostile archive can ship
     * {@code evil -> /home/user} followed by a child entry {@code evil/.bashrc}: the child's name
     * passes containment, but the real write follows the symlink out of the temp dir. Absolute
     * targets, and relative targets that climb above {@code baseDir}, are treated as escaping. The
     * target resolves against the link's own directory, the way the OS dereferences it, so a
     * legitimate in-tree link such as {@code bin/sh -> ../usr/bin/sh} is still allowed.
     */
    public static boolean symlinkTargetEscapes(Path baseDir, Path linkPath, String target) {
        try {
            Path base = baseDir.normalize();
            Path parent = linkPath.getParent();
            Path from = parent != null ? parent.normalize() : base;
            return !from.resolve(Path.of(target)).normalize().startsWith(base);
        }
        catch (Exception e) {
            // Unparseable target (illegal chars, cross-scheme, …) — refuse it, the safe default.
            return true;
        }
    }

    /**
     * Rebuilds an archive without risking the original: {@code write} emits the new archive into a
     * sibling temp file, which is moved over {@code archivePath} only after it finishes. A crash,
     * disk-full, or unreadable entry mid-repack then leaves the original intact instead of
     * truncating it into an unrecoverable half-written file — the temp dir it was extracted into is
     * deleted on close, so the on-disk archive is often the only surviving copy of the data.
     *
     * <p>The temp file is a sibling — same directory, hence same filesystem — so the move can be
     * atomic.
     */
    public static void repackAtomically(Path archivePath, ArchiveWriter write) throws IOException {
        Path dir = archivePath.toAbsolutePath().normalize().getParent();
        if (dir == null) {
            throw new IOException("Cannot repack an archive with no parent directory: " + archivePath);
        }

        Path tmp = Files.createTempFile(dir, "tc-repack-", ".tmp");
        try {
            write.writeTo(tmp);
            try {
                Files.move(tmp, archivePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException e) {
                // Rare (some network filesystems): a non-atomic replace is still far safer than
                // truncating the original before the new archive bytes exist.
                Files.move(tmp, archivePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (Throwable e) {
            try {
                Files.deleteIfExists(tmp);
            }
            catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    @FunctionalInterface
    public interface ArchiveWriter {
        void writeTo(Path target) throws IOException;
    }

    /**
     * Creates a placeholder file of {@code size} bytes for lazily-materialising filesystems. Opens
     * with {@link StandardOpenOption#SPARSE} so the OS can keep the allocation sparse where
     * supported, and falls back to a regular allocation otherwise. Either way {@link Files#size}
     * returns the right value, so listings show the real size before any bytes are streamed.
     */
    public static void createSparseStub(Path path, long size) throws IOException {
        Files.createFile(path);
        if (size <= 0) {
            return;
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.SPARSE)) {
            // Writing one byte at (size - 1) extends the file to `size`; on SPARSE-capable
            // filesystems the gap remains a hole. That byte is overwritten when materialize()
            // streams the real content.
            channel.position(size - 1);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }
    }

    public static String vfsRelativePath(Path root, Path path) {
        try {
            return root.relativize(path).toString();
        }
        catch (IllegalArgumentException e) {
            // Different roots (e.g. different drives on Windows)
            return path.toString();
        }
    }

    public static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static @Nullable Path containedOrNull(Path baseDir, Path resolved) {
        return resolved.normalize().startsWith(baseDir.normalize()) ? resolved : null;
    }

    private static String sanitize(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '/' || ch == '\\') {
                sb.append(ch);
            }
            else if (ch < ' ' || isIllegal(ch)) {
                sb.append('_');
            }
            else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static boolean isIllegal(char ch) {
        for (char illegal : ILLEGAL_FILENAME_CHARS) {
            if (ch == illegal) {
                return true;
            }
        }
        return false;
    }
}
