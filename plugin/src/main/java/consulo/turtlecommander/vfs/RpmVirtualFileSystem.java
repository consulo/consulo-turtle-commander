package consulo.turtlecommander.vfs;

import consulo.logging.Logger;
import consulo.util.lang.StringUtil;
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.Locale;

/**
 * Read-only filesystem for RPM packages. An RPM is laid out as:
 *
 * <pre>[lead 96 B] [signature header] [pad to 8 B] [main header] [compressed cpio payload]</pre>
 *
 * <p>Each header begins with a 16-byte intro (3-byte magic {@code 8e ad e8}, 1-byte version
 * {@code 01}, 4 reserved bytes, big-endian uint32 index count, big-endian uint32 data-store size),
 * followed by {@code nindex} 16-byte index entries and {@code hsize} bytes of string/numeric data.
 * The signature header is padded to an 8-byte boundary; the main header is not. Past both headers
 * the rest of the file is one compressed cpio stream, in the format named by tag 1125
 * (RPMTAG_PAYLOADCOMPRESSOR). The cpio entries carry the file names, typically prefixed "./".
 *
 * <p>RPMs are signed, so write-back would invalidate the package — extract once and stay read-only.
 */
public class RpmVirtualFileSystem extends AbstractTempDirVirtualFileSystem {
    private static final Logger LOG = Logger.getInstance(RpmVirtualFileSystem.class);

    private static final int RPMTAG_PAYLOADCOMPRESSOR = 1125;
    private static final int TYPE_STRING = 6;

    // Generous sanity caps so a malformed header can't trick us into a huge allocation. Real RPMs
    // have a few hundred index entries and data stores under a megabyte.
    private static final int MAX_INDEX_ENTRIES = 1_000_000;
    private static final int MAX_DATA_STORE = 64 * 1024 * 1024;

    private static final int LEAD_SIZE = 96;
    private static final int HEADER_INTRO_SIZE = 16;
    private static final int INDEX_ENTRY_SIZE = 16;

    public RpmVirtualFileSystem(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        super(archivePath, "turtle-rpm-", openProgress);
        openTempDir();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    protected void extract(Path into) throws IOException {
        VfsOpenProgress progress = takeOpenProgress();

        try (BufferedInputStream raw = new BufferedInputStream(Files.newInputStream(getArchivePath()))) {
            DataInputStream data = new DataInputStream(raw);

            skipLead(data);
            // The signature header is padded to an 8-byte boundary; the main header isn't.
            int sigBytes = skipHeader(data);
            skipFully(data, (8 - (sigBytes % 8)) % 8);

            String compressor = readMainHeaderCompressor(data);

            try (InputStream payload = wrapCompressor(raw, compressor);
                 CpioArchiveInputStream cpio = new CpioArchiveInputStream(payload)) {
                extractCpio(cpio, into, progress);
            }
        }
    }

    private void extractCpio(CpioArchiveInputStream cpio, Path into, VfsOpenProgress progress) throws IOException {
        CpioArchiveEntry entry = cpio.getNextEntry();
        while (entry != null) {
            if (progress.isCancelled()) {
                break;
            }

            String name = stripLeadingPath(entry.getName());
            progress.onEntry(0, 0, name);

            if (name.isEmpty() || name.equals("TRAILER!!!")) {
                entry = cpio.getNextEntry();
                continue;
            }

            Path entryPath = VfsPaths.resolveEntryPath(into, name);
            if (entryPath == null) {
                entry = cpio.getNextEntry();
                continue;
            }

            try {
                writeCpioEntry(cpio, entry, into, entryPath, name);

                Date lastModified = entry.getLastModifiedDate();
                if (lastModified != null && lastModified.getTime() > 0) {
                    Files.setLastModifiedTime(entryPath, FileTime.fromMillis(lastModified.getTime()));
                }
            }
            catch (Exception e) {
                LOG.debug("Failed to extract rpm entry: " + name, e);
            }

            entry = cpio.getNextEntry();
        }
    }

    private void writeCpioEntry(CpioArchiveInputStream cpio, CpioArchiveEntry entry, Path into,
                                Path entryPath, String name) throws IOException {
        if (entry.isDirectory()) {
            Files.createDirectories(entryPath);
            return;
        }

        if (entry.isSymbolicLink()) {
            // cpio stores the link target as the entry body.
            String target = new String(cpio.readAllBytes(), StandardCharsets.UTF_8);
            if (VfsPaths.symlinkTargetEscapes(into, entryPath, target)) {
                // Refuse a link pointing outside the extraction root — see TarVirtualFileSystem for
                // the symlink-then-child escape this prevents.
                LOG.warn("Skipping rpm symlink escaping the archive root: " + name + " -> " + target);
                return;
            }
            Path parent = entryPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try {
                Files.createSymbolicLink(entryPath, Path.of(target));
            }
            catch (Exception e) {
                // Windows without SeCreateSymbolicLinkPrivilege, or an odd target. Materialize as a
                // plain text file so the contents aren't lost.
                Files.writeString(entryPath, target);
            }
            return;
        }

        Path parent = entryPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(cpio, entryPath);
    }

    private static void skipLead(DataInputStream data) throws IOException {
        byte[] magic = new byte[4];
        data.readFully(magic);
        if (magic[0] != (byte)0xed || magic[1] != (byte)0xab || magic[2] != (byte)0xee || magic[3] != (byte)0xdb) {
            throw new IOException("Not an RPM file: missing 0xedabeedb lead magic");
        }
        skipFully(data, LEAD_SIZE - 4);
    }

    /**
     * Reads a header intro + index + data store, returning the number of bytes consumed from the
     * intro onward so callers can compute the signature header's 8-byte padding.
     */
    private static int skipHeader(DataInputStream data) throws IOException {
        byte[] intro = readHeaderIntro(data);
        int nindex = beUInt32(intro, 8);
        int hsize = beUInt32(intro, 12);
        validateHeaderBounds(nindex, hsize, "header");

        int indexBytes = nindex * INDEX_ENTRY_SIZE;
        skipFully(data, (long)indexBytes + hsize);
        return HEADER_INTRO_SIZE + indexBytes + hsize;
    }

    /**
     * Walks the main header looking for RPMTAG_PAYLOADCOMPRESSOR. Falls back to gzip when absent —
     * what {@code rpm} itself assumes for legacy v3 packages that predate the tag.
     */
    private static String readMainHeaderCompressor(DataInputStream data) throws IOException {
        byte[] intro = readHeaderIntro(data);
        int nindex = beUInt32(intro, 8);
        int hsize = beUInt32(intro, 12);
        validateHeaderBounds(nindex, hsize, "main header");

        byte[] index = new byte[nindex * INDEX_ENTRY_SIZE];
        data.readFully(index);
        byte[] store = new byte[hsize];
        data.readFully(store);

        for (int i = 0; i < nindex; i++) {
            int tag = beUInt32(index, i * INDEX_ENTRY_SIZE);
            int type = beUInt32(index, i * INDEX_ENTRY_SIZE + 4);
            int offset = beUInt32(index, i * INDEX_ENTRY_SIZE + 8);
            if (tag == RPMTAG_PAYLOADCOMPRESSOR && type == TYPE_STRING) {
                String value = readCString(store, offset);
                return value.isEmpty() ? "gzip" : value;
            }
        }
        return "gzip";
    }

    private static byte[] readHeaderIntro(DataInputStream data) throws IOException {
        byte[] intro = new byte[HEADER_INTRO_SIZE];
        data.readFully(intro);
        if (intro[0] != (byte)0x8e || intro[1] != (byte)0xad || intro[2] != (byte)0xe8 || intro[3] != (byte)0x01) {
            throw new IOException("Bad RPM header magic");
        }
        return intro;
    }

    private static void validateHeaderBounds(int nindex, int hsize, String what) throws IOException {
        if (nindex < 0 || hsize < 0 || nindex > MAX_INDEX_ENTRIES || hsize > MAX_DATA_STORE) {
            throw new IOException("RPM " + what + " bounds out of range (nindex=" + nindex + ", hsize=" + hsize + ")");
        }
    }

    private static InputStream wrapCompressor(InputStream raw, String name) throws IOException {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "gzip" -> new GzipCompressorInputStream(raw);
            case "bzip2" -> new BZip2CompressorInputStream(raw);
            case "xz" -> new XZCompressorInputStream(raw);
            case "lzma" -> new LZMACompressorInputStream(raw);
            case "zstd" -> new ZstdCompressorInputStream(raw);
            default -> throw new IOException("Unsupported RPM payload compressor: " + name);
        };
    }

    private static void skipFully(DataInputStream data, long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            long skipped = data.skip(remaining);
            if (skipped <= 0) {
                if (data.read() == -1) {
                    throw new IOException("Unexpected EOF in RPM header");
                }
                remaining--;
            }
            else {
                remaining -= skipped;
            }
        }
    }

    /** RPM headers cap individual fields well below 2^31, so a signed int is sufficient. */
    private static int beUInt32(byte[] buf, int off) {
        return ((buf[off] & 0xFF) << 24)
            | ((buf[off + 1] & 0xFF) << 16)
            | ((buf[off + 2] & 0xFF) << 8)
            | (buf[off + 3] & 0xFF);
    }

    private static String readCString(byte[] store, int offset) {
        if (offset < 0 || offset >= store.length) {
            return "";
        }
        int end = offset;
        while (end < store.length && store[end] != 0) {
            end++;
        }
        return new String(store, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static String stripLeadingPath(String name) {
        return StringUtil.trimLeading(StringUtil.trimStart(name, "./"), '/');
    }
}
