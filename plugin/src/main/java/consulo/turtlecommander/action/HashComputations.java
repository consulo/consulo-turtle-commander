package consulo.turtlecommander.action;

import consulo.application.progress.ProgressIndicator;
import consulo.turtlecommander.vfs.OpenVfsRegistry;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

/** Checksums over a file's bytes, reported and cancellable through a progress indicator. */
public final class HashComputations {
    private static final int BUFFER_SIZE = 64 * 1024;

    private HashComputations() {
    }

    public static String crc16(Path path, ProgressIndicator indicator) throws IOException {
        return withStream(path, indicator, HashComputations::crc16);
    }

    public static String crc32(Path path, ProgressIndicator indicator) throws IOException {
        return withStream(path, indicator, HashComputations::crc32);
    }

    public static String digest(Path path, String algorithm, ProgressIndicator indicator)
        throws IOException, NoSuchAlgorithmException {
        // MessageDigest.getInstance throws, so this can't reuse the checked-exception-free helper.
        OpenVfsRegistry.materializeIfNeeded(path);
        long total = sizeOrUnknown(path);
        try (InputStream input = Files.newInputStream(path)) {
            return digest(input, algorithm, total, indicator);
        }
    }

    @FunctionalInterface
    private interface StreamHash {
        String compute(InputStream input, long total, @Nullable ProgressIndicator indicator)
            throws IOException;
    }

    private static String withStream(Path path, ProgressIndicator indicator, StreamHash hash)
        throws IOException {
        // A lazily-materialised archive entry is a sparse stub until its bytes are streamed in;
        // hashing it without this would checksum a file full of zeroes.
        OpenVfsRegistry.materializeIfNeeded(path);
        long total = sizeOrUnknown(path);
        try (InputStream input = Files.newInputStream(path)) {
            return hash.compute(input, total, indicator);
        }
    }

    private static long sizeOrUnknown(Path path) {
        try {
            return Files.size(path);
        }
        catch (IOException e) {
            // Unknown size just means an indeterminate progress bar.
            return -1L;
        }
    }

    /** CRC-16/CCITT-FALSE: seed 0xFFFF, polynomial 0x1021, no reflection. */
    public static String crc16(InputStream input, long total, @Nullable ProgressIndicator indicator)
        throws IOException {
        int crc = 0xFFFF;
        byte[] buffer = new byte[BUFFER_SIZE];
        long processed = 0;

        int read;
        while ((read = readChunk(input, buffer, indicator)) > 0) {
            for (int i = 0; i < read; i++) {
                crc ^= (buffer[i] & 0xFF) << 8;
                for (int bit = 0; bit < 8; bit++) {
                    crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
                }
            }
            processed += read;
            reportProgress(indicator, processed, total);
        }
        return String.format("%04X", crc & 0xFFFF);
    }

    public static String crc32(InputStream input, long total, @Nullable ProgressIndicator indicator)
        throws IOException {
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[BUFFER_SIZE];
        long processed = 0;

        int read;
        while ((read = readChunk(input, buffer, indicator)) > 0) {
            crc.update(buffer, 0, read);
            processed += read;
            reportProgress(indicator, processed, total);
        }
        return String.format("%08X", crc.getValue());
    }

    public static String digest(InputStream input, String algorithm, long total,
                                @Nullable ProgressIndicator indicator)
        throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] buffer = new byte[BUFFER_SIZE];
        long processed = 0;

        int read;
        while ((read = readChunk(input, buffer, indicator)) > 0) {
            digest.update(buffer, 0, read);
            processed += read;
            reportProgress(indicator, processed, total);
        }
        return toHex(digest.digest());
    }

    private static int readChunk(InputStream input, byte[] buffer, @Nullable ProgressIndicator indicator)
        throws IOException {
        if (indicator != null) {
            indicator.checkCanceled();
        }
        return input.read(buffer);
    }

    private static void reportProgress(@Nullable ProgressIndicator indicator, long processed, long total) {
        if (indicator != null && total > 0) {
            indicator.setFraction((double) processed / total);
        }
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
