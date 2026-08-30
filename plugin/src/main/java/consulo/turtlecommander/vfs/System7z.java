package consulo.turtlecommander.vfs;

import consulo.application.util.SystemInfo;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Bridge to a locally installed 7-Zip CLI, used when commons-compress can't decode an archive. */
final class System7z {
    private static final int TAIL_LINES = 30;
    private static final int MAX_PROGRESS_LINE = 120;

    private System7z() {
    }

    /** Locates a usable 7-Zip CLI, or null if none is installed / on PATH. */
    static @Nullable String findBinary() {
        List<String> candidates = SystemInfo.isWindows
            ? List.of("7z.exe", "7zz.exe", "7za.exe")
            // Order: 7z (full), 7zz (newer p7zip-zstd builds, macOS Homebrew),
            // 7za (standalone — supports BCJ2 too via bundled codecs).
            : List.of("7z", "7zz", "7za");

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String segment : pathEnv.split(String.valueOf(File.pathSeparatorChar))) {
                if (segment.isBlank()) {
                    continue;
                }
                Path pathDir;
                try {
                    pathDir = Path.of(segment);
                }
                catch (Exception e) {
                    continue;
                }
                for (String name : candidates) {
                    Path candidate = pathDir.resolve(name);
                    if (isExecutableFile(candidate)) {
                        return candidate.toString();
                    }
                }
            }
        }

        // Windows: the 7-Zip MSI doesn't add itself to PATH by default, so probe the standard
        // install locations.
        if (SystemInfo.isWindows) {
            for (String root : new String[]{
                System.getenv("ProgramW6432"),
                System.getenv("ProgramFiles"),
                System.getenv("ProgramFiles(x86)")
            }) {
                if (root == null || root.isBlank()) {
                    continue;
                }
                Path candidate;
                try {
                    candidate = Path.of(root, "7-Zip", "7z.exe");
                }
                catch (Exception e) {
                    continue;
                }
                if (isExecutableFile(candidate)) {
                    return candidate.toString();
                }
            }
        }
        return null;
    }

    /**
     * Extracts {@code archive} into {@code into} with the system 7-Zip CLI. {@code password} is
     * passed via {@code -p}; null/empty means "no password", which makes 7-Zip fail fast on
     * encrypted input rather than blocking on an interactive prompt. Honours cancellation through
     * {@code progress}. Throws when no binary is found or the run exits non-zero.
     */
    static void extract(Path archive, Path into, VfsOpenProgress progress, @Nullable String password)
        throws IOException, InterruptedException {

        String binary = findBinary();
        if (binary == null) {
            throw new IOException("System 7-Zip not found. Install p7zip / 7-Zip and ensure 7z, 7zz, "
                + "or 7za is on PATH (or installed in the standard 7-Zip location on Windows).");
        }

        Process process = new ProcessBuilder(
            binary,
            "x",                                  // extract preserving paths
            "-y",                                 // assume yes on overwrite prompts
            "-bd",                                // disable progress bar (clean output)
            "-p" + (password != null ? password : ""), // empty => fail on encrypted, don't prompt
            "-o" + into.toAbsolutePath(),
            archive.toAbsolutePath().toString()
        ).redirectErrorStream(true).start();

        // Close stdin so any unanticipated prompt sees EOF and the process exits instead of
        // deadlocking us.
        try {
            process.getOutputStream().close();
        }
        catch (IOException ignored) {
            // Nothing useful to do; the drain below still bounds the run.
        }

        // Drain the merged stdout/stderr into a small ring buffer so the tail can be surfaced in
        // the error message without unbounded log volume.
        Deque<String> tail = new ArrayDeque<>();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (progress.isCancelled()) {
                    process.destroy();
                    throw new InterruptedException("Cancelled");
                }
                String trimmed = line.trim();
                progress.onEntry(0, 0, trimmed.length() > MAX_PROGRESS_LINE
                    ? trimmed.substring(0, MAX_PROGRESS_LINE)
                    : trimmed);
                tail.addLast(line);
                if (tail.size() > TAIL_LINES) {
                    tail.removeFirst();
                }
            }
        }

        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("system 7-Zip exited with code " + exit + ". Tail of output:\n"
                + String.join("\n", tail));
        }
    }

    static void extract(Path archive, Path into, VfsOpenProgress progress) throws IOException, InterruptedException {
        extract(archive, into, progress, null);
    }

    private static boolean isExecutableFile(Path candidate) {
        return Files.isRegularFile(candidate) && Files.isExecutable(candidate);
    }
}
