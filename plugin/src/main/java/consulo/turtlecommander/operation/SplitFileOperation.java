package consulo.turtlecommander.operation;

import consulo.turtlecommander.vfs.OpenVfsRegistry;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;

/**
 * Splits a file into numbered chunks plus a {@code .crc} manifest that
 * {@link CombineFilesOperation} verifies against.
 */
public final class SplitFileOperation {
    private static final int BUFFER_SIZE = 64 * 1024;

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int chunkIndex, int totalChunks, long bytesWritten, long totalBytes);
    }

    private SplitFileOperation() {
    }

    /** On cancellation every chunk written so far is removed, leaving no partial split behind. */
    public static void split(Path sourceFile, Path targetDirectory, long chunkSize,
                             ProgressCallback onProgress, BooleanSupplier isCancelled) throws IOException {

        long fileSize = Files.size(sourceFile);
        String fileName = sourceFile.getFileName().toString();
        int totalChunks = fileSize == 0L ? 1 : (int)((fileSize + chunkSize - 1) / chunkSize);
        int extensionWidth = extensionWidth(totalChunks);

        Files.createDirectories(targetDirectory);

        CRC32 crc32 = new CRC32();
        long totalBytesRead = 0L;
        int chunkIndex = 0;
        List<Path> writtenFiles = new ArrayList<>();
        boolean cancelled = false;

        OpenVfsRegistry.materializeIfNeeded(sourceFile);

        try (InputStream input = new BufferedInputStream(Files.newInputStream(sourceFile), BUFFER_SIZE)) {
            if (fileSize == 0L) {
                // A zero-byte source still produces one (empty) chunk.
                Path chunkPath = chunkPath(targetDirectory, fileName, 1, extensionWidth);
                Files.createFile(chunkPath);
                writtenFiles.add(chunkPath);
                onProgress.onProgress(1, 1, 0L, 0L);
            }

            byte[] buffer = new byte[BUFFER_SIZE];
            while (totalBytesRead < fileSize) {
                if (isCancelled.getAsBoolean()) {
                    cancelled = true;
                    break;
                }

                chunkIndex++;
                Path chunkPath = chunkPath(targetDirectory, fileName, chunkIndex, extensionWidth);
                long chunkBytesWritten = 0L;

                try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(chunkPath), BUFFER_SIZE)) {
                    while (chunkBytesWritten < chunkSize) {
                        if (isCancelled.getAsBoolean()) {
                            cancelled = true;
                            break;
                        }

                        int toRead = (int)Math.min(BUFFER_SIZE, chunkSize - chunkBytesWritten);
                        int bytesRead = input.read(buffer, 0, toRead);
                        if (bytesRead == -1) {
                            break;
                        }

                        output.write(buffer, 0, bytesRead);
                        crc32.update(buffer, 0, bytesRead);
                        chunkBytesWritten += bytesRead;
                        totalBytesRead += bytesRead;

                        onProgress.onProgress(chunkIndex, totalChunks, totalBytesRead, fileSize);
                    }
                }

                writtenFiles.add(chunkPath);
                if (cancelled) {
                    break;
                }
            }
        }

        if (cancelled) {
            for (Path file : writtenFiles) {
                Files.deleteIfExists(file);
            }
            return;
        }

        writeCrcFile(targetDirectory, fileName, fileSize, crc32.getValue());
    }

    private static Path chunkPath(Path targetDirectory, String fileName, int chunkIndex, int extensionWidth) {
        String ext = String.format("%0" + extensionWidth + "d", chunkIndex);
        return targetDirectory.resolve(fileName + "." + ext);
    }

    private static void writeCrcFile(Path targetDirectory, String fileName, long fileSize, long crc32Value)
        throws IOException {

        String content = "filename=" + fileName + "\nsize=" + fileSize
            + "\ncrc32=" + String.format("%08X", crc32Value) + "\n";
        Files.writeString(targetDirectory.resolve(fileName + ".crc"), content);
    }

    private static int extensionWidth(int totalChunks) {
        if (totalChunks <= 999) {
            return 3;
        }
        if (totalChunks <= 9999) {
            return 4;
        }
        if (totalChunks <= 99999) {
            return 5;
        }
        return 6;
    }
}
