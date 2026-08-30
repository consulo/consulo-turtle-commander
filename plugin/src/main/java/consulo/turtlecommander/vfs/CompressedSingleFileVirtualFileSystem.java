package consulo.turtlecommander.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * A single compressed file (.gz / .bz2 / .xz that isn't a tarball) presented as a one-entry
 * filesystem. Read-only: there is no meaningful way to write back a single stream without
 * re-deriving the original container.
 */
public class CompressedSingleFileVirtualFileSystem extends AbstractTempDirVirtualFileSystem {
    @FunctionalInterface
    public interface DecompressorFactory {
        InputStream wrap(InputStream source) throws IOException;
    }

    private final String myCompressionSuffix;
    private final DecompressorFactory myDecompressorFactory;

    public CompressedSingleFileVirtualFileSystem(Path archivePath, String compressionSuffix,
                                                 DecompressorFactory decompressorFactory) throws IOException {
        super(archivePath, "turtle-decompress-", null);
        myCompressionSuffix = compressionSuffix;
        myDecompressorFactory = decompressorFactory;
        openTempDir();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    protected void extract(Path into) throws IOException {
        Path fileName = getArchivePath().getFileName();
        String originalName = fileName != null ? fileName.toString() : "file";

        String innerName = originalName.toLowerCase(Locale.ROOT).endsWith(myCompressionSuffix.toLowerCase(Locale.ROOT))
            ? originalName.substring(0, originalName.length() - myCompressionSuffix.length())
            : originalName;

        Path destPath = into.resolve(innerName);
        try (InputStream stream = myDecompressorFactory.wrap(Files.newInputStream(getArchivePath()))) {
            Files.copy(stream, destPath);
        }
    }
}
