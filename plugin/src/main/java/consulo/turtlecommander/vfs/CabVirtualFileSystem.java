package consulo.turtlecommander.vfs;

import de.morihofi.cab4j.extract.CabExtractor;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;

/** Microsoft Cabinet archives. Read-only: cab4j can extract but not write. */
public class CabVirtualFileSystem extends AbstractTempDirVirtualFileSystem {
    private static final byte[] CAB_SIGNATURE = {0x4D, 0x53, 0x43, 0x46};

    public CabVirtualFileSystem(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        super(archivePath, "turtle-cab-", openProgress);
        openTempDir();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    protected void extract(Path into) throws IOException {
        VfsOpenProgress progress = takeOpenProgress();
        if (!sniffIsCab()) {
            throw new SilentVfsOpenException();
        }

        try {
            cab4jExtract(into, progress);
        }
        catch (IOException e) {
            throw e;
        }
        catch (Exception e) {
            reset(into);
            sevenZipFallback(into, progress, e);
        }
    }

    private void cab4jExtract(Path into, VfsOpenProgress progress) throws IOException {
        Map<String, CabExtractor.FileData> entries = CabExtractor.extractWithAttributes(
            ByteBuffer.wrap(Files.readAllBytes(getArchivePath())));

        int total = entries.size();
        int index = 0;

        for (Map.Entry<String, CabExtractor.FileData> entry : entries.entrySet()) {
            if (progress.isCancelled()) {
                break;
            }
            progress.onEntry(++index, total, entry.getKey());

            Path target = VfsPaths.resolveEntryPath(into, entry.getKey().replace('\\', '/'));
            if (target == null) {
                continue;
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            CabExtractor.FileData fileData = entry.getValue();
            ByteBuffer data = fileData.data.duplicate();
            data.rewind();
            try (FileChannel out = FileChannel.open(target, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                while (data.hasRemaining()) {
                    out.write(data);
                }
            }

            try {
                Files.setLastModifiedTime(target,
                    FileTime.from(fileData.lastModified.atZone(ZoneId.systemDefault()).toInstant()));
            }
            catch (Exception e) {
                // A timestamp we can't apply is not worth failing the extraction over.
            }
        }
    }

    private void sevenZipFallback(Path into, VfsOpenProgress progress, Exception cause) throws IOException {
        if (System7z.findBinary() == null) {
            throw new IOException("This CAB archive couldn't be read in-process. Install 7-Zip / p7zip and "
                + "ensure 7z, 7zz, or 7za is on PATH (or in the standard 7-Zip install location on Windows) "
                + "to open it.", cause);
        }
        try {
            System7z.extract(getArchivePath(), into, progress);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("CAB extraction was cancelled", e);
        }
    }

    private boolean sniffIsCab() {
        byte[] header = new byte[CAB_SIGNATURE.length];
        try (InputStream stream = Files.newInputStream(getArchivePath())) {
            int read = stream.readNBytes(header, 0, header.length);
            return read == CAB_SIGNATURE.length && Arrays.equals(header, CAB_SIGNATURE);
        }
        catch (Exception e) {
            return false;
        }
    }

    private static void reset(Path dir) throws IOException {
        VfsPaths.deleteRecursively(dir);
        Files.createDirectories(dir);
    }

}
