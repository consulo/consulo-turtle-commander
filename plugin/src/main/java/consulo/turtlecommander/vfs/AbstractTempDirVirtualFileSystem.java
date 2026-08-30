package consulo.turtlecommander.vfs;

import consulo.logging.Logger;
import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Common scaffolding for filesystems that materialise an archive into a temp directory and operate
 * on it from there: extract once at open, answer listings from the temp dir, repack into the
 * archive on flush, delete the temp dir on close.
 *
 * <p>Subclasses supply the format-specific bits via {@link #extract} (mandatory) and
 * {@link #repack} (no-op default for read-only formats), and must call {@link #openTempDir()} from
 * their own constructor once their fields are initialised — {@link #extract} typically needs them.
 */
public abstract class AbstractTempDirVirtualFileSystem implements VirtualFileSystem {
    private static final Logger LOG = Logger.getInstance(AbstractTempDirVirtualFileSystem.class);

    private final Path myArchivePath;
    private final String myTempDirPrefix;

    private Path myTempDir = Path.of("");

    // One-shot open-progress hook, cleared on first take so a flush()-driven re-extract can't call
    // into a progress reporter whose scope has already ended.
    private @Nullable VfsOpenProgress myOpenProgress;

    /**
     * Fingerprint of the temp-dir contents captured right after extract. {@link #flush} compares
     * against it so a repack only happens when something actually changed — copying a file out of
     * the archive, or a plain refresh, must not rewrite (and re-compress) an untouched archive.
     *
     * <p>Granularity caveat: an in-place edit preserving both size and mtime goes unnoticed, but
     * every real write path (editor write-back, copy-in, delete) produces a fresh mtime or a
     * different size. Lazy implementations keep materialisation invisible here by giving the
     * materialised file the stub's exact mtime.
     */
    private long myCleanFingerprint;

    /**
     * Size + mtime of the archive as of the last extract. {@link #flush} re-extracts only when this
     * changed (an external program rewrote the archive) or the temp dir is dirty; otherwise the
     * existing temp dir, with any lazily-materialised content, is kept as is.
     */
    private @Nullable ArchiveSignature myCleanArchiveSignature;

    private record ArchiveSignature(long size, FileTime lastModified) {
    }

    protected AbstractTempDirVirtualFileSystem(Path archivePath, String tempDirPrefix,
                                               @Nullable VfsOpenProgress openProgress) {
        myArchivePath = archivePath;
        myTempDirPrefix = tempDirPrefix;
        myOpenProgress = openProgress;
    }

    @Override
    public Path getArchivePath() {
        return myArchivePath;
    }

    protected Path getTempDir() {
        return myTempDir;
    }

    /**
     * The progress hook for the current {@link #extract} run: the constructor-supplied hook on the
     * first call, the thread-bound indicator (or a no-op) afterwards. Call once at the top of
     * {@link #extract}.
     */
    protected VfsOpenProgress takeOpenProgress() {
        VfsOpenProgress progress = myOpenProgress != null ? myOpenProgress : VfsOpenProgress.fromCurrentThread();
        myOpenProgress = null;
        return progress;
    }

    /**
     * Extracts the archive into {@code into}, an empty temp directory. Throwing aborts
     * initialisation; {@link #openTempDir()} wipes {@code into} before propagating.
     */
    protected abstract void extract(Path into) throws IOException;

    /**
     * Repacks the temp dir back into the archive. The no-op default suits read-only formats; the
     * base {@link #flush} only invokes this when {@link #isReadOnly()} is false.
     */
    protected void repack(Path from) throws IOException {
    }

    /**
     * Fired after a successful rename move but before {@link #repack}. Subclasses maintaining
     * rename-aware bookkeeping (e.g. the ar filesystem's per-entry mode/uid/gid map) override this
     * to migrate state from the old name to the new.
     */
    protected void onRenamed(Path source, Path target) {
    }

    /**
     * Creates a new temp directory and runs {@link #extract} into it. On any failure the partial
     * directory is wiped before the exception propagates, so a malformed archive doesn't leak temp
     * files.
     */
    protected final void openTempDir() throws IOException {
        // Captured before extract so an archive rewritten mid-extraction can't be mistaken for the
        // state we extracted — the next flush would then re-extract, the safe direction.
        ArchiveSignature signature = archiveSignature();
        Path dir = Files.createTempDirectory(myTempDirPrefix);
        try {
            extract(dir);
        }
        catch (Throwable e) {
            try {
                VfsPaths.deleteRecursively(dir);
            }
            catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }

        myTempDir = dir;
        myCleanFingerprint = computeFingerprint();
        myCleanArchiveSignature = signature;
        // Register only after the temp dir is fully populated (or stub-populated) so
        // materializeIfNeeded never dispatches to a half-initialised VFS. The matching unregister
        // lives in close(), not flush(), because flush() rebuilds the temp dir while the VFS lives.
        OpenVfsRegistry.register(this);
    }

    @Override
    public Path getRoot() {
        return myTempDir;
    }

    @Override
    public boolean isRoot(Path path) {
        return path.normalize().equals(myTempDir.normalize());
    }

    @Override
    public Path getPath(String relativePath) {
        String clean = relativePath;
        while (clean.startsWith("/") || clean.startsWith("\\")) {
            clean = clean.substring(1);
        }
        return clean.isEmpty() ? myTempDir : myTempDir.resolve(clean);
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
            result.add(VfsPaths.parentEntry(parent != null ? parent : myTempDir));
        }

        result.addAll(VfsPaths.readDirectoryEntries(directory, this::entryMetadata));
        return result;
    }

    @Override
    public Path renameFile(Path source, String newName) throws IOException {
        if (isReadOnly()) {
            throw new UnsupportedOperationException("Cannot rename inside a read-only archive");
        }
        Path parent = source.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Cannot rename a root path");
        }

        Path target = parent.resolve(newName);
        Files.move(source, target);
        onRenamed(source, target);
        repack(myTempDir);

        // The archive matches the temp dir again; without this a following flush() would see the
        // renamed entry as a change and repack (and re-extract) a second time.
        myCleanFingerprint = computeFingerprint();
        myCleanArchiveSignature = archiveSignature();
        return target;
    }

    @Override
    public void flush() throws IOException {
        // Repack only when the temp dir actually changed: read-only operations (copying a file out
        // of the archive, a plain refresh) must not rewrite the archive.
        boolean dirty = !isReadOnly() && computeFingerprint() != myCleanFingerprint;
        if (dirty) {
            repack(myTempDir);
        }
        else {
            ArchiveSignature signature = archiveSignature();
            if (signature != null && signature.equals(myCleanArchiveSignature)) {
                // Nothing to pick up in either direction: the temp dir is untouched and the archive
                // on disk is still the one we extracted from. Keep the temp dir (and any lazily
                // materialised content) instead of re-extracting.
                return;
            }
        }

        VfsPaths.deleteRecursively(myTempDir);
        openTempDir();
    }

    @Override
    public void close() {
        OpenVfsRegistry.unregister(this);
        try {
            VfsPaths.deleteRecursively(myTempDir);
        }
        catch (Exception e) {
            LOG.debug("Failed to clean up temp dir " + myTempDir, e);
        }
    }

    private long computeFingerprint() throws IOException {
        long h = 1125899906842597L;
        for (VfsPaths.ArchiveEntry entry : VfsPaths.collectArchiveEntries(myTempDir)) {
            BasicFileAttributes attrs = Files.readAttributes(entry.path(), BasicFileAttributes.class);
            h = h * 31 + entry.relativeName().hashCode();
            if (attrs.isDirectory()) {
                // Directories contribute by name only: their mtime changes whenever a child is
                // created — including the sibling temp file a lazy materialize writes — and carries
                // no archive-relevant information the file set and per-file size/mtime don't.
                h = h * 31 - 1L;
            }
            else {
                h = h * 31 + attrs.size();
                h = h * 31 + attrs.lastModifiedTime().toMillis();
            }
        }
        return h;
    }

    private @Nullable ArchiveSignature archiveSignature() {
        try {
            return new ArchiveSignature(Files.size(myArchivePath), Files.getLastModifiedTime(myArchivePath));
        }
        catch (IOException e) {
            return null;
        }
    }
}
