package consulo.turtlecommander.service;

import consulo.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Removes stale {@code turtle-*} temp directories left over from previous plugin sessions — the
 * {@code turtle-vfs-*} dirs hosting files extracted for viewing/editing, and the per-format
 * extraction dirs of the temp-dir filesystems.
 *
 * <p>Normal teardown deletes them via the disposer; this sweep is the safety net for JVM crashes
 * ({@code File.deleteOnExit} is unreliable across crashes and accumulates registrations forever).
 *
 * <p>Runs once per JVM, the first time any archive temp dir is created, and only removes
 * directories older than {@link #MAX_AGE_MS} so a concurrently running instance isn't clobbered.
 */
public final class VfsTempCleanup {
    private static final Logger LOG = Logger.getInstance(VfsTempCleanup.class);

    private static final List<String> PREFIXES = List.of(
        "turtle-vfs-",
        // Archive entries extracted for a drag out of the panel.
        "turtle-dnd-",
        // AbstractTempDirVirtualFileSystem subclasses; see their constructor arguments.
        "turtle-zip-", "turtle-tar-", "turtle-rar-", "turtle-7z-", "turtle-iso-",
        "turtle-pak-", "turtle-rpm-", "turtle-crx-", "turtle-ar-", "turtle-cab-", "turtle-decompress-"
    );

    static final long MAX_AGE_MS = 60L * 60 * 1000;

    private static final AtomicBoolean ourDone = new AtomicBoolean(false);

    private VfsTempCleanup() {
    }

    public static void cleanupOnce() {
        if (!ourDone.compareAndSet(false, true)) {
            return;
        }
        try {
            cleanNow(defaultTempDir(), MAX_AGE_MS);
        }
        catch (Exception e) {
            LOG.debug("VfsTempCleanup failed", e);
        }
    }

    static Path defaultTempDir() {
        return Path.of(System.getProperty("java.io.tmpdir"));
    }

    /** Removes stale {@code turtle-*} dirs in {@code tempDir} older than {@code maxAgeMs}. */
    static int cleanNow(Path tempDir, long maxAgeMs) throws IOException {
        if (!Files.isDirectory(tempDir)) {
            return 0;
        }

        long cutoff = System.currentTimeMillis() - maxAgeMs;
        int removed = 0;

        try (Stream<Path> stream = Files.list(tempDir)) {
            for (Path path : stream.toList()) {
                Path fileName = path.getFileName();
                if (fileName == null || !hasKnownPrefix(fileName.toString())) {
                    continue;
                }

                FileTime mtime;
                try {
                    mtime = Files.getLastModifiedTime(path);
                }
                catch (IOException e) {
                    continue;
                }

                if (mtime.toMillis() > cutoff) {
                    continue;
                }
                if (deleteRecursive(path)) {
                    removed++;
                }
            }
        }
        return removed;
    }

    static void resetForTesting() {
        ourDone.set(false);
    }

    private static boolean hasKnownPrefix(String name) {
        for (String prefix : PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean deleteRecursive(Path path) {
        if (!Files.exists(path)) {
            return false;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(child -> {
                try {
                    Files.deleteIfExists(child);
                }
                catch (IOException ignored) {
                    // A file we can't remove just leaves the sweep incomplete for this dir.
                }
            });
            return !Files.exists(path);
        }
        catch (IOException e) {
            return false;
        }
    }
}
