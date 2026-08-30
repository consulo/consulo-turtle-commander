package consulo.turtlecommander.vfs;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The archive formats Turtle Commander can browse. Order matters only where extensions overlap;
 * the first provider whose {@link VirtualFileSystemProvider#supports} accepts a path wins.
 */
public final class VirtualFileSystemRegistry {
    private static final List<VirtualFileSystemProvider> ourProviders = new CopyOnWriteArrayList<>(List.of(
        new ZipFileSystemProvider(),
        new CompressedTarFileSystemProvider.Gz(),
        new CompressedTarFileSystemProvider.Bz2(),
        new CompressedTarFileSystemProvider.Xz(),
        new TarFileSystemProvider(),
        new SevenZipFileSystemProvider(),
        new RarFileSystemProvider(),
        new CabFileSystemProvider(),
        new ArFileSystemProvider(),
        new CrxFileSystemProvider(),
        new RpmFileSystemProvider(),
        new PakFileSystemProvider(),
        new IsoFileSystemProvider()
    ));

    private VirtualFileSystemRegistry() {
    }

    public static void register(VirtualFileSystemProvider provider) {
        ourProviders.add(provider);
    }

    public static boolean supports(Path path) {
        for (VirtualFileSystemProvider provider : ourProviders) {
            if (provider.supports(path)) {
                return true;
            }
        }
        return false;
    }

    public static boolean supportsByExtension(String fileName) {
        String extension = VirtualFileSystemProvider.extensionOf(fileName);
        for (VirtualFileSystemProvider provider : ourProviders) {
            if (provider.supportsExtension(extension)) {
                return true;
            }
        }
        return false;
    }

    public static VirtualFileSystem create(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        for (VirtualFileSystemProvider provider : ourProviders) {
            if (provider.supports(archivePath)) {
                return provider.create(archivePath, openProgress);
            }
        }
        throw new IllegalArgumentException("No VFS provider for: " + archivePath);
    }

    public static VirtualFileSystem create(Path archivePath) throws IOException {
        return create(archivePath, null);
    }
}
