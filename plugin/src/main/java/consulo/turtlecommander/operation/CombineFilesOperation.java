package consulo.turtlecommander.operation;

import consulo.turtlecommander.util.FileUtils;
import consulo.turtlecommander.vfs.OpenVfsRegistry;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;

/** Reassembles the chunks produced by {@link SplitFileOperation}, verifying size and CRC. */
public final class CombineFilesOperation {
    private static final int BUFFER_SIZE = 64 * 1024;

    public record CrcInfo(String filename, long size, long crc32) {
    }

    private CombineFilesOperation() {
    }

    public static CrcInfo parseCrcFile(Path crcFile) throws IOException {
        Map<String, String> props = new HashMap<>();
        for (String line : Files.readAllLines(crcFile)) {
            int idx = line.indexOf('=');
            if (idx > 0) {
                props.put(line.substring(0, idx).trim().toLowerCase(Locale.ROOT), line.substring(idx + 1).trim());
            }
        }

        String filename = props.get("filename");
        if (filename == null) {
            throw new IllegalArgumentException("CRC file missing 'filename' field");
        }

        String sizeValue = props.get("size");
        long size;
        try {
            if (sizeValue == null) {
                throw new NumberFormatException();
            }
            size = Long.parseLong(sizeValue);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("CRC file missing or invalid 'size' field");
        }

        String crc32Str = props.get("crc32");
        if (crc32Str == null) {
            throw new IllegalArgumentException("CRC file missing 'crc32' field");
        }
        if (crc32Str.isEmpty() || crc32Str.length() > 8) {
            throw new IllegalArgumentException("Invalid CRC32 value: " + crc32Str);
        }
        // Parsed unsigned: a CRC32 with the high bit set overflows a signed 32-bit parse.
        long crc32 = Long.parseLong(crc32Str, 16) & 0xFFFFFFFFL;

        return new CrcInfo(filename, size, crc32);
    }

    public static List<Path> findChunkFiles(Path directory, String baseFileName) {
        List<Path> chunks = new ArrayList<>();
        int index = 1;
        while (true) {
            Path chunk = findChunkFile(directory, baseFileName, index);
            if (chunk == null) {
                break;
            }
            chunks.add(chunk);
            index++;
        }
        return chunks;
    }

    /** The base name a {@code .crc} manifest or a {@code .001}-style chunk belongs to. */
    public static @Nullable String resolveBaseFileName(Path selectedFile) {
        String name = selectedFile.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex <= 0) {
            return null;
        }

        String ext = name.substring(dotIndex + 1);
        if (ext.isEmpty()) {
            return null;
        }
        if (ext.equals("crc")) {
            return name.substring(0, dotIndex);
        }
        for (int i = 0; i < ext.length(); i++) {
            if (!Character.isDigit(ext.charAt(i))) {
                return null;
            }
        }
        return name.substring(0, dotIndex);
    }

    /** On cancellation or a verification failure the partial target file is removed. */
    public static void combine(List<Path> chunkFiles, Path targetFile, @Nullable Long expectedSize,
                               @Nullable Long expectedCrc32, SplitFileOperation.ProgressCallback onProgress,
                               BooleanSupplier isCancelled) throws IOException {

        long totalBytes;
        if (expectedSize != null && expectedSize > 0) {
            totalBytes = expectedSize;
        }
        else {
            totalBytes = 0;
            for (Path chunk : chunkFiles) {
                totalBytes += Files.size(chunk);
            }
        }

        CRC32 crc32 = new CRC32();
        long totalBytesWritten = 0L;
        boolean cancelled = false;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(targetFile), BUFFER_SIZE)) {
            for (int idx = 0; idx < chunkFiles.size(); idx++) {
                if (isCancelled.getAsBoolean()) {
                    cancelled = true;
                    break;
                }

                Path chunk = chunkFiles.get(idx);
                OpenVfsRegistry.materializeIfNeeded(chunk);

                try (InputStream input = new BufferedInputStream(Files.newInputStream(chunk), BUFFER_SIZE)) {
                    while (true) {
                        if (isCancelled.getAsBoolean()) {
                            cancelled = true;
                            break;
                        }

                        int bytesRead = input.read(buffer);
                        if (bytesRead == -1) {
                            break;
                        }

                        output.write(buffer, 0, bytesRead);
                        crc32.update(buffer, 0, bytesRead);
                        totalBytesWritten += bytesRead;

                        onProgress.onProgress(idx + 1, chunkFiles.size(), totalBytesWritten, totalBytes);
                    }
                }

                if (cancelled) {
                    break;
                }
            }
        }

        if (cancelled) {
            Files.deleteIfExists(targetFile);
            return;
        }

        if (expectedSize != null && totalBytesWritten != expectedSize) {
            Files.deleteIfExists(targetFile);
            throw new IllegalStateException("Size mismatch: expected " + FileUtils.formatSize(expectedSize)
                + " but got " + FileUtils.formatSize(totalBytesWritten));
        }

        if (expectedCrc32 != null && crc32.getValue() != expectedCrc32) {
            Files.deleteIfExists(targetFile);
            throw new IllegalStateException("CRC mismatch: expected " + String.format("%08X", expectedCrc32)
                + " but got " + String.format("%08X", crc32.getValue()));
        }
    }

    private static @Nullable Path findChunkFile(Path directory, String baseFileName, int index) {
        for (int width = 3; width <= 6; width++) {
            Path path = directory.resolve(baseFileName + "." + String.format("%0" + width + "d", index));
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }
}
