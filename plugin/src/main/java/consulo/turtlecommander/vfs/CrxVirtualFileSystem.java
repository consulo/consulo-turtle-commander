package consulo.turtlecommander.vfs;

import consulo.logging.Logger;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jspecify.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.Enumeration;

/**
 * Chrome / Chromium extension packages ({@code .crx}). A CRX file is a ZIP prefixed with a
 * signature header:
 *
 * <ul>
 * <li>CRX2: {@code Cr24} magic, version=2, public-key length, signature length, then the key
 * bytes, signature bytes, and finally the ZIP payload.</li>
 * <li>CRX3: {@code Cr24} magic, version=3, header length, the header proto, and the ZIP.</li>
 * </ul>
 *
 * <p>All length fields are little-endian uint32. The header is parsed, the ZIP payload streamed
 * into a temp file, then extracted. Read-only: any edit would break the signature and make the
 * result unloadable by the browser, so write-back has no useful semantics.
 */
public class CrxVirtualFileSystem extends AbstractTempDirVirtualFileSystem {
    private static final Logger LOG = Logger.getInstance(CrxVirtualFileSystem.class);

    private static final byte[] CRX_MAGIC = {'C', 'r', '2', '4'};

    public CrxVirtualFileSystem(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        super(archivePath, "turtle-crx-", openProgress);
        openTempDir();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    protected void extract(Path into) throws IOException {
        VfsOpenProgress progress = takeOpenProgress();
        Path innerZip = Files.createTempFile("turtle-crx-inner-", ".zip");
        try {
            try (InputStream input = Files.newInputStream(getArchivePath())) {
                skipCrxHeader(input);
                try (OutputStream output = Files.newOutputStream(innerZip)) {
                    input.transferTo(output);
                }
            }

            extractInnerZip(innerZip, into, progress);
        }
        finally {
            try {
                Files.deleteIfExists(innerZip);
            }
            catch (Exception e) {
                LOG.debug("Failed to delete inner crx zip " + innerZip, e);
            }
        }
    }

    private void extractInnerZip(Path innerZip, Path into, VfsOpenProgress progress) throws IOException {
        try (ZipFile zip = ZipFile.builder().setPath(innerZip).get()) {
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

                try {
                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    }
                    else {
                        Path parent = entryPath.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        try (InputStream stream = zip.getInputStream(entry)) {
                            Files.copy(stream, entryPath);
                        }
                    }

                    Date lastModified = entry.getLastModifiedDate();
                    if (lastModified != null) {
                        Files.setLastModifiedTime(entryPath, FileTime.fromMillis(lastModified.getTime()));
                    }
                }
                catch (Exception e) {
                    LOG.debug("Failed to extract crx entry: " + entry.getName(), e);
                }
            }
        }
    }

    private static void skipCrxHeader(InputStream input) throws IOException {
        DataInputStream data = new DataInputStream(input);

        byte[] magic = new byte[CRX_MAGIC.length];
        data.readFully(magic);
        if (!java.util.Arrays.equals(magic, CRX_MAGIC)) {
            throw new IOException("Not a CRX file: missing 'Cr24' magic header");
        }

        long version = readLeUInt32(data);
        if (version == 2L) {
            long pubKeyLen = readLeUInt32(data);
            long sigLen = readLeUInt32(data);
            skipFully(data, pubKeyLen + sigLen);
        }
        else if (version == 3L) {
            skipFully(data, readLeUInt32(data));
        }
        else {
            throw new IOException("Unsupported CRX version: " + version);
        }
    }

    private static long readLeUInt32(DataInputStream data) throws IOException {
        byte[] b = new byte[4];
        data.readFully(b);
        return (long)(b[0] & 0xFF)
            | ((long)(b[1] & 0xFF) << 8)
            | ((long)(b[2] & 0xFF) << 16)
            | ((long)(b[3] & 0xFF) << 24);
    }

    private static void skipFully(DataInputStream data, long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            long skipped = data.skip(remaining);
            if (skipped <= 0) {
                // skip() can return 0 prematurely on some streams; force a single-byte read so an
                // unexpected EOF surfaces as an explicit error.
                if (data.read() == -1) {
                    throw new IOException("Unexpected EOF in CRX header");
                }
                remaining--;
            }
            else {
                remaining -= skipped;
            }
        }
    }
}
