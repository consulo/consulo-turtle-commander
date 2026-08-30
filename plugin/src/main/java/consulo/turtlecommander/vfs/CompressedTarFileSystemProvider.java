package consulo.turtlecommander.vfs;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Shared provider for the gzip / bzip2 / xz families. A name matching one of the tarball suffixes
 * opens as a {@link TarVirtualFileSystem} wrapped in the matching compressor; anything else is a
 * single compressed file.
 */
public abstract class CompressedTarFileSystemProvider implements VirtualFileSystemProvider {
    private final Set<String> myExtensions;
    private final Set<String> myTarballSuffixes;
    private final String myCompressionSuffix;

    protected CompressedTarFileSystemProvider(Set<String> extensions, Set<String> tarballSuffixes,
                                              String compressionSuffix) {
        myExtensions = extensions;
        myTarballSuffixes = tarballSuffixes;
        myCompressionSuffix = compressionSuffix;
    }

    protected abstract TarVirtualFileSystem.InputStreamFactory tarInput();

    protected abstract TarVirtualFileSystem.OutputStreamFactory tarOutput();

    protected abstract CompressedSingleFileVirtualFileSystem.DecompressorFactory decompressor();

    @Override
    public boolean supportsExtension(String extension) {
        return myExtensions.contains(extension);
    }

    @Override
    public VirtualFileSystem create(Path archivePath) throws IOException {
        return create(archivePath, null);
    }

    @Override
    public VirtualFileSystem create(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        if (isTarball(archivePath)) {
            return new TarVirtualFileSystem(archivePath, tarInput(), tarOutput(), openProgress);
        }
        return new CompressedSingleFileVirtualFileSystem(archivePath, myCompressionSuffix, decompressor());
    }

    private boolean isTarball(Path archivePath) {
        Path fileName = archivePath.getFileName();
        String name = fileName != null ? fileName.toString().toLowerCase(Locale.ROOT) : "";
        for (String suffix : myTarballSuffixes) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    public static class Gz extends CompressedTarFileSystemProvider {
        public static final Set<String> ARCHIVE_EXTENSIONS = Set.of("gz", "tgz");

        public Gz() {
            super(ARCHIVE_EXTENSIONS, Set.of(".tar.gz", ".tgz"), ".gz");
        }

        @Override
        protected TarVirtualFileSystem.InputStreamFactory tarInput() {
            return path -> new GzipCompressorInputStream(Files.newInputStream(path));
        }

        @Override
        protected TarVirtualFileSystem.OutputStreamFactory tarOutput() {
            return path -> new GzipCompressorOutputStream(Files.newOutputStream(path));
        }

        @Override
        protected CompressedSingleFileVirtualFileSystem.DecompressorFactory decompressor() {
            return GzipCompressorInputStream::new;
        }
    }

    public static class Bz2 extends CompressedTarFileSystemProvider {
        public static final Set<String> ARCHIVE_EXTENSIONS = Set.of("bz2", "tbz2", "tbz");

        public Bz2() {
            super(ARCHIVE_EXTENSIONS, Set.of(".tar.bz2", ".tbz2", ".tbz"), ".bz2");
        }

        @Override
        protected TarVirtualFileSystem.InputStreamFactory tarInput() {
            return path -> new BZip2CompressorInputStream(Files.newInputStream(path));
        }

        @Override
        protected TarVirtualFileSystem.OutputStreamFactory tarOutput() {
            return path -> new BZip2CompressorOutputStream(Files.newOutputStream(path));
        }

        @Override
        protected CompressedSingleFileVirtualFileSystem.DecompressorFactory decompressor() {
            return BZip2CompressorInputStream::new;
        }
    }

    public static class Xz extends CompressedTarFileSystemProvider {
        public static final Set<String> ARCHIVE_EXTENSIONS = Set.of("xz", "txz");

        public Xz() {
            super(ARCHIVE_EXTENSIONS, Set.of(".tar.xz", ".txz"), ".xz");
        }

        @Override
        protected TarVirtualFileSystem.InputStreamFactory tarInput() {
            return path -> new XZCompressorInputStream(Files.newInputStream(path));
        }

        @Override
        protected TarVirtualFileSystem.OutputStreamFactory tarOutput() {
            return path -> new XZCompressorOutputStream(Files.newOutputStream(path));
        }

        @Override
        protected CompressedSingleFileVirtualFileSystem.DecompressorFactory decompressor() {
            return XZCompressorInputStream::new;
        }
    }
}
