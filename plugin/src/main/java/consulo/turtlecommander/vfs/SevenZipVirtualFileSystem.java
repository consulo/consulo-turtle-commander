package consulo.turtlecommander.vfs;

import consulo.logging.Logger;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.List;

public class SevenZipVirtualFileSystem extends AbstractTempDirVirtualFileSystem {
    private static final Logger LOG = Logger.getInstance(SevenZipVirtualFileSystem.class);

    /**
     * Substrings from commons-compress 1.28's SevenZFile / Coders error sites. Each signals a
     * decoder gap the system 7-Zip CLI can typically bridge: BCJ2 stacks, a missing XZ-for-Java,
     * codecs not yet implemented, or header features the library refuses.
     */
    private static final List<String> UNSUPPORTED_CODER_MARKERS = List.of(
        "Multi input/output stream coders",
        "Unsupported compression method",
        "BCJ filter",
        "Alternative methods are unsupported",
        "Additional streams unsupported"
    );

    public SevenZipVirtualFileSystem(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        super(archivePath, "turtle-7z-", openProgress);
        openTempDir();
    }

    @Override
    protected void extract(Path into) throws IOException {
        VfsOpenProgress progress = takeOpenProgress();
        try {
            extractWithCommonsCompress(into, progress);
        }
        catch (Exception e) {
            // commons-compress can't decode multi-stream coders (BCJ2, certain delta variants,
            // some encrypted modes). Fall back to the user's installed 7-Zip CLI before giving up —
            // it handles everything 7-Zip itself can produce.
            if (!looksLikeUnsupportedCoder(e)) {
                throw e;
            }

            // Wipe whatever commons-compress wrote. openTempDir cleans up on a final throw, but the
            // retry happens inside extract(), so clear partial state before the second attempt.
            VfsPaths.deleteRecursively(into);
            Files.createDirectory(into);

            try {
                System7z.extract(getArchivePath(), into, progress);
            }
            catch (Exception toolError) {
                throw new IOException(
                    "This 7z archive uses a coder commons-compress doesn't support (e.g. BCJ2). "
                        + "Tried system 7-Zip but: "
                        + (toolError.getMessage() != null ? toolError.getMessage() : toolError.getClass().getSimpleName()),
                    toolError);
            }
        }
    }

    @Override
    protected void repack(Path from) throws IOException {
        VfsPaths.repackAtomically(getArchivePath(), target -> {
            try (SevenZOutputFile out = new SevenZOutputFile(target.toFile())) {
                // SevenZOutputFile duck-types as an OutputStream (write(int)/write(byte[])/
                // write(byte[],int,int)) but doesn't extend it, so it can't be handed to
                // Files.copy directly. A thin adapter buys the JDK's tuned copy path.
                OutputStream sink = new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        out.write(b);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        out.write(b, off, len);
                    }
                };

                for (VfsPaths.ArchiveEntry archiveEntry : VfsPaths.collectArchiveEntries(from)) {
                    try {
                        BasicFileAttributes attrs =
                            Files.readAttributes(archiveEntry.path(), BasicFileAttributes.class);

                        SevenZArchiveEntry entry = new SevenZArchiveEntry();
                        entry.setName(archiveEntry.relativeName() + (attrs.isDirectory() ? "/" : ""));
                        entry.setDirectory(attrs.isDirectory());
                        if (!attrs.isDirectory()) {
                            entry.setSize(attrs.size());
                        }
                        entry.setLastModifiedDate(new Date(attrs.lastModifiedTime().toMillis()));

                        out.putArchiveEntry(entry);
                        if (!attrs.isDirectory()) {
                            Files.copy(archiveEntry.path(), sink);
                        }
                        out.closeArchiveEntry();
                    }
                    catch (IOException e) {
                        LOG.warn("Failed to repack entry: " + archiveEntry.relativeName(), e);
                    }
                }
            }
        });
    }

    static boolean looksLikeUnsupportedCoder(Throwable e) {
        for (Throwable current = e; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message == null) {
                continue;
            }
            for (String marker : UNSUPPORTED_CODER_MARKERS) {
                if (message.contains(marker)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void extractWithCommonsCompress(Path dir, VfsOpenProgress progress) throws IOException {
        try (SevenZFile sevenZ = SevenZFile.builder().setPath(getArchivePath()).get()) {
            SevenZArchiveEntry entry = sevenZ.getNextEntry();
            while (entry != null) {
                if (progress.isCancelled()) {
                    break;
                }
                progress.onEntry(0, 0, entry.getName());

                Path entryPath = VfsPaths.resolveEntryPath(dir, entry.getName());
                if (entryPath == null) {
                    entry = sevenZ.getNextEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                }
                else {
                    Path parent = entryPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    try (OutputStream out = Files.newOutputStream(entryPath)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = sevenZ.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    }
                }

                try {
                    if (entry.getHasLastModifiedDate()) {
                        Files.setLastModifiedTime(entryPath, FileTime.fromMillis(entry.getLastModifiedDate().getTime()));
                    }
                }
                catch (Exception e) {
                    LOG.debug("Failed to set modified time for 7z entry: " + entry.getName(), e);
                }

                entry = sevenZ.getNextEntry();
            }
        }
    }
}
