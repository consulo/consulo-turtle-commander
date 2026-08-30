package consulo.turtlecommander.vfs;

import com.palantir.isofilereader.isofilereader.GenericInternalIsoFile;
import com.palantir.isofilereader.isofilereader.IsoFileReader;
import com.palantir.isofilereader.isofilereader.udf.UdfFormatException;
import consulo.logging.Logger;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only filesystem for disc images ({@code .iso}), backed by Palantir's isofilereader, which
 * auto-detects the on-disc format and supports both:
 *
 * <ul>
 * <li><b>ISO 9660</b> (ECMA-119) — the classic CD-ROM filesystem, plus Joliet long names and Rock
 * Ridge POSIX attributes.</li>
 * <li><b>UDF</b> — the filesystem behind DVD-Video, Blu-ray and many modern Windows install ISOs,
 * whose {@code boot.wim} / {@code install.wim} live in the UDF half of the bridge format and are
 * not reachable through ISO 9660 alone.</li>
 * </ul>
 *
 * <p>Content materialises lazily: at open time only the directory structure is written, with each
 * file a sparse stub of the correct size and timestamp. Bytes stream from the image only when
 * something reads them, so opening a 5 GB Windows ISO costs a tree walk rather than 5 GB of disk.
 */
public class IsoVirtualFileSystem extends AbstractTempDirVirtualFileSystem {
    private static final Logger LOG = Logger.getInstance(IsoVirtualFileSystem.class);

    /**
     * Stub path (normalised) to the ISO record holding its bytes. Populated during extract and
     * drained by materialize. Concurrent because content search and parallel hash actions can race.
     */
    private final Map<Path, GenericInternalIsoFile> myPendingEntries = new ConcurrentHashMap<>();

    /**
     * Kept open for the filesystem's lifetime so materialize can stream individual files without
     * re-parsing the volume descriptors and tree each time.
     */
    private @Nullable IsoFileReader myReader;

    public IsoVirtualFileSystem(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        super(archivePath, "turtle-iso-", openProgress);
        openTempDir();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    protected void extract(Path into) throws IOException {
        VfsOpenProgress progress = takeOpenProgress();

        // flush() re-extracts into a fresh temp dir: release the previous round's reader and stub
        // bookkeeping first.
        closeReader();
        myPendingEntries.clear();

        IsoFileReader newReader = new IsoFileReader(getArchivePath().toFile());
        // The constructor runs format detection and silently picks the best mode it can. If it
        // finds neither an ISO 9660 volume descriptor nor a UDF anchor, reject the file explicitly
        // — otherwise any random file would mount as an empty filesystem.
        if (!newReader.hasIsoFormat() && !newReader.hasUdfFormat()) {
            newReader.close();
            throw new IOException("Not an ISO 9660 or UDF disc image: " + getArchivePath().getFileName());
        }

        try {
            GenericInternalIsoFile[] tree = newReader.getAllFiles();
            List<GenericInternalIsoFile> files = newReader.convertTreeFilesToFlatList(tree);

            for (int index = 0; index < files.size(); index++) {
                if (progress.isCancelled()) {
                    break;
                }
                stubEntry(into, files.get(index), index + 1, files.size(), progress);
            }
        }
        catch (UdfFormatException e) {
            newReader.close();
            throw new IOException("Unreadable UDF structure in " + getArchivePath().getFileName(), e);
        }
        catch (Throwable e) {
            newReader.close();
            throw e;
        }

        myReader = newReader;
    }

    private void stubEntry(Path into, GenericInternalIsoFile entry, int index, int total,
                           VfsOpenProgress progress) {
        String rawPath = entry.getFullFileName('/');
        if (rawPath == null) {
            return;
        }

        String cleanPath = StringUtil.trimLeading(stripIsoVersionSuffix(rawPath), '/');
        if (cleanPath.isEmpty()) {
            return;
        }
        progress.onEntry(index, total, cleanPath);

        Path entryPath = VfsPaths.resolveEntryPath(into, cleanPath);
        if (entryPath == null) {
            return;
        }

        try {
            Path parent = entryPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            VfsPaths.createSparseStub(entryPath, entry.getSize());
            applyEntryDate(entryPath, entry);
            myPendingEntries.put(entryPath.normalize(), entry);
        }
        catch (Exception e) {
            LOG.debug("Failed to stub iso entry: " + cleanPath, e);
        }
    }

    /**
     * Streams the actual bytes for {@code path} and replaces its sparse stub. A no-op when the path
     * isn't one of ours or was already materialised. Synchronised so two consumers reading the same
     * file concurrently each get the full content rather than racing through the copy; the monitor
     * is held only for the duration of the I/O, so different files still materialise in parallel.
     */
    @Override
    public synchronized void materialize(Path path) {
        Path normalized = path.normalize();
        GenericInternalIsoFile entry = myPendingEntries.get(normalized);
        if (entry == null) {
            return;
        }
        IsoFileReader activeReader = myReader;
        if (activeReader == null) {
            return;
        }

        try {
            // Write into a sibling temp file then atomic-move, so a partial write never leaves a
            // half-streamed file at the canonical path.
            Path tmp = Files.createTempFile(normalized.getParent(), ".tc-materializing-", ".tmp");
            try {
                try (InputStream input = activeReader.getFileStream(entry)) {
                    Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(tmp, normalized, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            finally {
                Files.deleteIfExists(tmp);
            }

            applyEntryDate(normalized, entry);
            myPendingEntries.remove(normalized);
        }
        catch (IOException e) {
            LOG.warn("Failed to materialize ISO entry " + normalized, e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        closeReader();
        myPendingEntries.clear();
        super.close();
    }

    private void closeReader() {
        IsoFileReader reader = myReader;
        if (reader != null) {
            try {
                reader.close();
            }
            catch (Exception e) {
                LOG.debug("Failed to close IsoFileReader for " + getArchivePath(), e);
            }
        }
        myReader = null;
    }

    private static void applyEntryDate(Path path, GenericInternalIsoFile entry) throws IOException {
        Optional<Date> date = entry.getDateAsDate();
        if (date != null && date.isPresent()) {
            Files.setLastModifiedTime(path, FileTime.fromMillis(date.get().getTime()));
        }
    }

    /**
     * ISO 9660 file identifiers end with {@code ;N} where N is the file version. Real images almost
     * always use version 1, and every tool that mounts ISOs hides the suffix, so we do too — the
     * materialised filename then matches what the user expects. UDF entries carry no suffix, so
     * this is a no-op for them.
     */
    private static String stripIsoVersionSuffix(String path) {
        int semi = path.lastIndexOf(';');
        if (semi < 0) {
            return path;
        }
        // Only strip when everything after `;` is digits — anything else is part of the name.
        for (int i = semi + 1; i < path.length(); i++) {
            if (!Character.isDigit(path.charAt(i))) {
                return path;
            }
        }
        return path.substring(0, semi);
    }

}
