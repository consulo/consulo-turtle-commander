package consulo.turtlecommander.service;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.logging.Logger;
import consulo.turtlecommander.operation.TarOutputStream;
import consulo.turtlecommander.vfs.OpenVfsRegistry;
import consulo.turtlecommander.vfs.VfsOpenProgress;
import consulo.turtlecommander.vfs.VirtualFileSystem;
import consulo.turtlecommander.vfs.VirtualFileSystemRegistry;
import jakarta.inject.Singleton;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.jspecify.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPOutputStream;

/**
 * Packing and extraction. Every method blocks; callers run them off the UI thread and drive
 * cancellation through the supplied predicate.
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class ArchiveService {
    private static final Logger LOG = Logger.getInstance(ArchiveService.class);

    private static final List<String> ZIP_SUFFIXES = List.of(
        ".zip", ".jar", ".war", ".ear", ".apk", ".aar", ".aab", ".apkg", ".vsix", ".ipa",
        ".appx", ".appxbundle", ".msix", ".msixbundle", ".xps", ".oxps", ".kmz", ".usdz",
        ".oxt", ".pk3", ".pk4"
    );

    private enum ExtractAction {
        OVERWRITE, SKIP, CANCEL
    }

    @FunctionalInterface
    private interface CompressorFactory {
        InputStream wrap(InputStream raw) throws IOException;
    }

    public int packZip(Path archivePath, List<Path> sourcePaths, boolean appendToExisting, boolean archiveExists,
                       ProgressListener onProgress, ErrorListener onError, BooleanSupplier isCancelled)
        throws IOException {

        Map<String, String> env = new HashMap<>();
        if (!appendToExisting && !archiveExists) {
            env.put("create", "true");
        }
        if (!appendToExisting && archiveExists) {
            Files.delete(archivePath);
            env.put("create", "true");
        }

        URI uri = URI.create("jar:" + archivePath.toUri());

        // One counter: the progress UI and the return value must agree on what "packed" means.
        // Only files count — directory entries are created implicitly and aren't interesting ticks.
        int[] successCount = {0};

        try (FileSystem zipFs = FileSystems.newFileSystem(uri, env)) {
            for (Path source : sourcePaths) {
                if (isCancelled.getAsBoolean()) {
                    break;
                }

                if (Files.isDirectory(source)) {
                    Path base = source.getParent() != null ? source.getParent() : source;
                    Files.walkFileTree(source, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            if (isCancelled.getAsBoolean()) {
                                return FileVisitResult.TERMINATE;
                            }
                            try {
                                Files.createDirectories(zipFs.getPath(relativize(base, dir)));
                            }
                            catch (FileAlreadyExistsException e) {
                                // Already present from a previous entry.
                            }
                            catch (IOException e) {
                                onError.onError(dir, e);
                            }
                            // Report the name for UX but don't tick the counter — otherwise the
                            // final count would exceed the number of files actually written.
                            onProgress.onProgress(successCount[0], dir.getFileName().toString());
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (isCancelled.getAsBoolean()) {
                                return FileVisitResult.TERMINATE;
                            }
                            try {
                                OpenVfsRegistry.materializeIfNeeded(file);
                                Files.copy(file, zipFs.getPath(relativize(base, file)),
                                    StandardCopyOption.REPLACE_EXISTING);
                                successCount[0]++;
                                onProgress.onProgress(successCount[0], file.getFileName().toString());
                            }
                            catch (Exception e) {
                                onError.onError(file, e);
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            if (isCancelled.getAsBoolean()) {
                                return FileVisitResult.TERMINATE;
                            }
                            onError.onError(file, exc);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
                else {
                    try {
                        OpenVfsRegistry.materializeIfNeeded(source);
                        Files.copy(source, zipFs.getPath(source.getFileName().toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                        successCount[0]++;
                        onProgress.onProgress(successCount[0], source.getFileName().toString());
                    }
                    catch (Exception e) {
                        onError.onError(source, e);
                    }
                }
            }
        }

        return successCount[0];
    }

    public int packTarGz(Path archivePath, List<Path> sourcePaths, ProgressListener onProgress,
                         ErrorListener onError, BooleanSupplier isCancelled) throws IOException {

        int[] successCount = {0};

        try (OutputStream fos = new BufferedOutputStream(Files.newOutputStream(archivePath));
             GZIPOutputStream gzos = new GZIPOutputStream(fos);
             TarOutputStream tarOs = new TarOutputStream(gzos)) {

            for (Path source : sourcePaths) {
                if (isCancelled.getAsBoolean()) {
                    break;
                }

                if (Files.isDirectory(source)) {
                    Path base = source.getParent() != null ? source.getParent() : source;
                    Files.walkFileTree(source, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            if (isCancelled.getAsBoolean()) {
                                return FileVisitResult.TERMINATE;
                            }
                            String relativePath = relativize(base, dir);
                            if (!relativePath.isEmpty()) {
                                try {
                                    tarOs.putDirectoryEntry(relativePath + "/", attrs.lastModifiedTime().toMillis());
                                }
                                catch (IOException e) {
                                    onError.onError(dir, e);
                                }
                            }
                            onProgress.onProgress(successCount[0], dir.getFileName().toString());
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (isCancelled.getAsBoolean()) {
                                return FileVisitResult.TERMINATE;
                            }
                            try {
                                tarOs.putFileEntry(relativize(base, file), file, attrs.lastModifiedTime().toMillis());
                                successCount[0]++;
                                onProgress.onProgress(successCount[0], file.getFileName().toString());
                            }
                            catch (Exception e) {
                                onError.onError(file, e);
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            if (isCancelled.getAsBoolean()) {
                                return FileVisitResult.TERMINATE;
                            }
                            onError.onError(file, exc);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
                else {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(source, BasicFileAttributes.class);
                        tarOs.putFileEntry(source.getFileName().toString(), source,
                            attrs.lastModifiedTime().toMillis());
                        successCount[0]++;
                        onProgress.onProgress(successCount[0], source.getFileName().toString());
                    }
                    catch (Exception e) {
                        onError.onError(source, e);
                    }
                }
            }

            tarOs.finish();
        }

        return successCount[0];
    }

    public int countArchiveEntries(Path archivePath, @Nullable VfsOpenProgress openProgress) {
        // Prefer a header-only scan: a VFS-based count extracts to a temp directory, doubling the
        // work of a subsequent extract. For known formats the entries enumerate without
        // decompressing bodies.
        try {
            int fast = countArchiveEntriesFast(archivePath);
            if (fast >= 0) {
                return fast;
            }
        }
        catch (Exception e) {
            LOG.debug("Header-only count failed for " + archivePath + ", falling back to VFS walk", e);
        }
        return countArchiveEntriesViaVfs(archivePath, openProgress);
    }

    /** Entry count from a header-only scan, or -1 when the format isn't recognised. */
    int countArchiveEntriesFast(Path archivePath) throws IOException {
        // An archive living as a stub inside a lazy parent (an .iso) must be streamed to disk
        // before the header-only readers can inspect it.
        OpenVfsRegistry.materializeIfNeeded(archivePath);

        Path fileName = archivePath.getFileName();
        if (fileName == null) {
            return -1;
        }
        String name = fileName.toString().toLowerCase(Locale.ROOT);

        if (endsWithAny(name, ZIP_SUFFIXES)) {
            return countZipEntries(archivePath);
        }
        if (name.endsWith(".7z") || name.endsWith(".cb7")) {
            return countSevenZEntries(archivePath);
        }
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return countTarCompressed(archivePath, GzipCompressorInputStream::new);
        }
        if (name.endsWith(".tar.bz2") || name.endsWith(".tbz2") || name.endsWith(".tbz")) {
            return countTarCompressed(archivePath, BZip2CompressorInputStream::new);
        }
        if (name.endsWith(".tar.xz") || name.endsWith(".txz")) {
            return countTarCompressed(archivePath, XZCompressorInputStream::new);
        }
        if (name.endsWith(".tar") || name.endsWith(".cbt")) {
            return countTarEntries(Files.newInputStream(archivePath));
        }
        if (name.endsWith(".ar") || name.endsWith(".deb") || name.endsWith(".a")) {
            return countArEntries(archivePath);
        }
        // Standalone single-file compressors wrap exactly one inner file.
        if (name.endsWith(".gz") || name.endsWith(".bz2") || name.endsWith(".xz")) {
            return 1;
        }
        return -1;
    }

    public void extractArchiveWithProgress(Path archivePath, Path destination, OverwritePolicy initialPolicy,
                                           ProgressListener onProgress, OverwriteConfirmation onOverwriteConfirm,
                                           ErrorListener onError, BooleanSupplier isCancelled,
                                           @Nullable VfsOpenProgress openProgress) {
        int extractedCount = 0;
        OverwritePolicy[] policy = {initialPolicy};

        try (VirtualFileSystem vfs = VirtualFileSystemRegistry.create(archivePath, openProgress)) {
            Path root = vfs.getRoot();
            List<VfsEntry> entries = collectEntries(root);

            for (VfsEntry entry : entries) {
                if (isCancelled.getAsBoolean()) {
                    break;
                }

                if (entry.directory()) {
                    Path targetDir = destination.resolve(entry.relativePath());
                    try {
                        Files.createDirectories(targetDir);
                    }
                    catch (Exception e) {
                        onError.onError(targetDir, e);
                    }
                }
                else {
                    Path targetFile = destination.resolve(entry.relativePath());
                    try {
                        // Lazy filesystems populate the temp dir with sparse stubs; without this the
                        // copies below would write zero-filled files of the right size.
                        OpenVfsRegistry.materializeIfNeeded(entry.sourcePath());
                        Path parent = targetFile.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }

                        if (!Files.exists(targetFile)) {
                            Files.copy(entry.sourcePath(), targetFile);
                        }
                        else {
                            ExtractAction action = resolveExtractAction(entry.sourcePath(), targetFile,
                                policy[0], onOverwriteConfirm, updated -> policy[0] = updated);
                            if (action == ExtractAction.CANCEL) {
                                break;
                            }
                            if (action == ExtractAction.OVERWRITE) {
                                Files.copy(entry.sourcePath(), targetFile, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                    catch (Exception e) {
                        onError.onError(targetFile, e);
                    }
                }

                extractedCount++;
                onProgress.onProgress(extractedCount, entry.relativePath());
            }
        }
        catch (Exception e) {
            LOG.warn("Failed to extract archive " + archivePath, e);
            onError.onError(archivePath, e);
        }
    }

    private record VfsEntry(Path sourcePath, String relativePath, boolean directory) {
    }

    private static List<VfsEntry> collectEntries(Path root) throws IOException {
        List<VfsEntry> entries = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root)) {
                    entries.add(new VfsEntry(dir, root.relativize(dir).toString(), true));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                entries.add(new VfsEntry(file, root.relativize(file).toString(), false));
                return FileVisitResult.CONTINUE;
            }
        });
        return entries;
    }

    /**
     * Mirrors {@code FileOperationService}'s overwrite resolution for the extract path. Extract is
     * the one consumer that can't sit on that service directly — it works through a filesystem — so
     * the decision logic is duplicated rather than dragged through another dependency.
     */
    private static ExtractAction resolveExtractAction(Path source, Path target, OverwritePolicy policy,
                                                      OverwriteConfirmation onOverwriteConfirm,
                                                      java.util.function.Consumer<OverwritePolicy> setPolicy) {
        switch (policy) {
            case OVERWRITE_ALL:
                return ExtractAction.OVERWRITE;
            case SKIP_ALL:
                return ExtractAction.SKIP;
            case IF_OLDER:
                return ifOlder(source, target);
            case ASK:
            default:
                break;
        }

        return switch (onOverwriteConfirm.confirm(target)) {
            case OVERWRITE -> ExtractAction.OVERWRITE;
            case SKIP -> ExtractAction.SKIP;
            case OVERWRITE_ALL -> {
                setPolicy.accept(OverwritePolicy.OVERWRITE_ALL);
                yield ExtractAction.OVERWRITE;
            }
            case SKIP_ALL -> {
                setPolicy.accept(OverwritePolicy.SKIP_ALL);
                yield ExtractAction.SKIP;
            }
            case OVERWRITE_IF_OLDER -> ifOlder(source, target);
            case OVERWRITE_ALL_IF_OLDER -> {
                setPolicy.accept(OverwritePolicy.IF_OLDER);
                yield ifOlder(source, target);
            }
            case CANCEL -> ExtractAction.CANCEL;
        };
    }

    private static ExtractAction ifOlder(Path source, Path target) {
        boolean sourceNewer;
        try {
            sourceNewer = Files.getLastModifiedTime(source).compareTo(Files.getLastModifiedTime(target)) > 0;
        }
        catch (Exception e) {
            // mtime unreadable on either side — overwrite, so the user's intent ("replace stale
            // targets") wins over a transient stat failure.
            sourceNewer = true;
        }
        return sourceNewer ? ExtractAction.OVERWRITE : ExtractAction.SKIP;
    }

    private static int countZipEntries(Path archivePath) throws IOException {
        try (ZipFile zf = ZipFile.builder().setPath(archivePath).get()) {
            int count = 0;
            var entries = zf.getEntries();
            while (entries.hasMoreElements()) {
                entries.nextElement();
                count++;
            }
            return count;
        }
    }

    private static int countSevenZEntries(Path archivePath) throws IOException {
        try (SevenZFile szf = SevenZFile.builder().setPath(archivePath).get()) {
            int count = 0;
            while (szf.getNextEntry() != null) {
                count++;
            }
            return count;
        }
    }

    /**
     * Wraps the file stream with the compressor and counts tar entries. The raw stream is closed if
     * the compressor constructor throws — otherwise it would leak on corrupt archives.
     */
    int countTarCompressed(Path archivePath, CompressorFactory wrap) throws IOException {
        return countTarCompressed(Files.newInputStream(archivePath), wrap);
    }

    int countTarCompressed(InputStream raw, CompressorFactory wrap) throws IOException {
        InputStream wrapped;
        try {
            wrapped = wrap.wrap(raw);
        }
        catch (Throwable e) {
            try {
                raw.close();
            }
            catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
        return countTarEntries(wrapped);
    }

    private static int countTarEntries(InputStream raw) throws IOException {
        try (InputStream input = raw;
             TarArchiveInputStream tar = new TarArchiveInputStream(input)) {
            int count = 0;
            while (tar.getNextEntry() != null) {
                count++;
            }
            return count;
        }
    }

    private static int countArEntries(Path archivePath) throws IOException {
        try (InputStream input = Files.newInputStream(archivePath);
             ArArchiveInputStream ar = new ArArchiveInputStream(input)) {
            int count = 0;
            while (ar.getNextEntry() != null) {
                count++;
            }
            return count;
        }
    }

    private static int countArchiveEntriesViaVfs(Path archivePath, @Nullable VfsOpenProgress openProgress) {
        int[] count = {0};
        try (VirtualFileSystem vfs = VirtualFileSystemRegistry.create(archivePath, openProgress)) {
            Path root = vfs.getRoot();
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    count[0]++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root)) {
                        count[0]++;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (Exception e) {
            LOG.debug("VFS entry count failed for " + archivePath, e);
        }
        return count[0];
    }

    private static String relativize(Path base, Path path) {
        return base.relativize(path).toString().replace("\\", "/");
    }

    private static boolean endsWithAny(String name, List<String> suffixes) {
        for (String suffix : suffixes) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
