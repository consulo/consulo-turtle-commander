package consulo.turtlecommander.operation;

import consulo.turtlecommander.vfs.OpenVfsRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal tar writer producing POSIX ustar entries, with the GNU {@code @LongLink} extension for
 * names longer than 100 characters.
 */
public class TarOutputStream implements AutoCloseable {
    private static final int BLOCK_SIZE = 512;

    /** Largest size representable in an 11-digit octal ustar field: ~8 GB. */
    private static final long MAX_FILE_SIZE = 0x1FFFFFFFFL;

    private static final char TYPE_REGULAR = '0';
    private static final char TYPE_DIRECTORY = '5';
    private static final char TYPE_GNU_LONG_NAME = 'L';

    private static final long MODE_DIRECTORY = 0755;
    private static final long MODE_FILE = 0644;

    private final OutputStream myOut;

    public TarOutputStream(OutputStream out) {
        myOut = out;
    }

    public void putDirectoryEntry(String name, long modTimeMillis) throws IOException {
        writeLongNameIfNeeded(name);
        myOut.write(createHeader(truncateName(name), 0, modTimeMillis / 1000, TYPE_DIRECTORY));
    }

    public void putFileEntry(String name, Path file, long modTimeMillis) throws IOException {
        OpenVfsRegistry.materializeIfNeeded(file);

        long actualSize = Files.size(file);
        if (actualSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size " + actualSize
                + " exceeds maximum for ustar tar format (" + MAX_FILE_SIZE
                + " bytes). Use a different archive format for files larger than ~8 GB.");
        }

        writeLongNameIfNeeded(name);
        myOut.write(createHeader(truncateName(name), actualSize, modTimeMillis / 1000, TYPE_REGULAR));

        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            long remaining = actualSize;
            while (remaining > 0) {
                int toRead = (int)Math.min(buffer.length, remaining);
                int read = input.read(buffer, 0, toRead);
                if (read <= 0) {
                    break;
                }
                myOut.write(buffer, 0, read);
                remaining -= read;
            }
            if (remaining > 0) {
                throw new IOException("File " + file + " is shorter than declared size " + actualSize
                    + " (missing " + remaining + " bytes)");
            }
        }

        padToBlock(actualSize);
    }

    public void finish() throws IOException {
        // Two zero blocks mark the end of the archive.
        myOut.write(new byte[BLOCK_SIZE * 2]);
        myOut.flush();
    }

    @Override
    public void close() throws IOException {
        myOut.close();
    }

    private void writeLongNameIfNeeded(String name) throws IOException {
        if (name.length() <= 100) {
            return;
        }
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        myOut.write(createHeader("././@LongLink", nameBytes.length, 0, TYPE_GNU_LONG_NAME));
        myOut.write(nameBytes);
        padToBlock(nameBytes.length);
    }

    private void padToBlock(long written) throws IOException {
        int remainder = (int)(written % BLOCK_SIZE);
        if (remainder > 0) {
            myOut.write(new byte[BLOCK_SIZE - remainder]);
        }
    }

    private static String truncateName(String name) {
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private static byte[] createHeader(String name, long size, long modTime, char typeFlag) {
        byte[] header = new byte[BLOCK_SIZE];

        writeString(header, 0, name, 100);                                              // name
        writeOctal(header, 100, typeFlag == TYPE_DIRECTORY ? MODE_DIRECTORY : MODE_FILE, 8); // mode
        writeOctal(header, 108, 0, 8);                                                  // uid
        writeOctal(header, 116, 0, 8);                                                  // gid
        writeOctal(header, 124, size, 12);                                              // size
        writeOctal(header, 136, modTime, 12);                                           // mtime

        // Checksum field is spaces while the checksum itself is computed over the block.
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }

        header[156] = (byte)typeFlag;
        writeString(header, 257, "ustar", 6);
        header[262] = 0;
        header[263] = '0';
        header[264] = '0';

        long checksum = 0;
        for (byte b : header) {
            checksum += b & 0xFF;
        }
        writeOctal(header, 148, checksum, 7);
        header[155] = ' ';

        return header;
    }

    private static void writeString(byte[] buf, int offset, String value, int maxLen) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, buf, offset, Math.min(bytes.length, maxLen));
    }

    private static void writeOctal(byte[] buf, int offset, long value, int fieldLen) {
        byte[] bytes = String.format("%0" + (fieldLen - 1) + "o", value).getBytes(StandardCharsets.UTF_8);
        int start = bytes.length > fieldLen - 1 ? bytes.length - (fieldLen - 1) : 0;
        int len = Math.min(bytes.length - start, fieldLen - 1);
        System.arraycopy(bytes, start, buf, offset, len);
        buf[offset + len] = 0;
    }
}
