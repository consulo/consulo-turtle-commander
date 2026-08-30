package consulo.turtlecommander.util;

import consulo.turtlecommander.settings.FileSizeFormat;
import consulo.turtlecommander.settings.TurtleCommanderSettings;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;

public final class FileUtils {
    private static final String[] BINARY_UNITS = {"B", "KiB", "MiB", "GiB", "TiB"};
    private static final String[] SI_UNITS = {"B", "kB", "MB", "GB", "TB"};

    private FileUtils() {
    }

    /**
     * Blocking recursive count used to size transfer progress. Callers must invoke it off the UI
     * thread.
     */
    public static int countFiles(List<Path> sources) {
        int[] count = {0};
        for (Path source : sources) {
            if (Files.isDirectory(source)) {
                try {
                    Files.walkFileTree(source, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            count[0]++;
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            count[0]++;
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
                catch (IOException e) {
                    // an unreadable subtree simply contributes nothing to the total
                }
            }
            else {
                count[0]++;
            }
        }
        return count[0];
    }

    /**
     * If {@code pattern} looks like plain text (contains none of {@code * ? [ .}), wrap it with
     * {@code *…*} so a substring match works through a {@code glob:} PathMatcher. Otherwise
     * return it verbatim so the user's intent — wildcards, character classes, or a literal
     * filename with an extension — is respected as an anchored full match.
     *
     * <p>{@code .} counts as "this is an exact filename, not a substring": {@code report.pdf}
     * matches a file literally named that, not {@code oldreport.pdf.bak}.
     */
    public static String wrapAsSubstringGlobIfPlain(String pattern) {
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*' || c == '?' || c == '[' || c == '.') {
                return pattern;
            }
        }
        return "*" + pattern + "*";
    }

    public static String fileErrorMessage(Exception error) {
        if (error instanceof AccessDeniedException denied) {
            return "Access denied: " + denied.getFile();
        }
        String message = error.getMessage();
        return message != null ? message : error.getClass().getSimpleName();
    }

    /**
     * Reads the user-configured {@link FileSizeFormat}, falling back to
     * {@link FileSizeFormat#AUTO_BINARY} when the application service isn't available (headless
     * unit tests). The fallback matches the historical hardcoded behaviour.
     */
    public static String formatSize(long bytes) {
        return formatSize(bytes, currentFormat());
    }

    /** Always-human-readable size, honouring the user's SI vs binary choice. */
    public static String formatSizeAuto(long bytes) {
        return formatSizeAuto(bytes, currentFormat());
    }

    private static FileSizeFormat currentFormat() {
        try {
            return TurtleCommanderSettings.getInstance().getFileSizeFormat();
        }
        catch (Exception e) {
            return FileSizeFormat.AUTO_BINARY;
        }
    }

    public static String formatSize(long bytes, FileSizeFormat format) {
        return switch (format) {
            case BYTES -> String.format(Locale.ROOT, "%,d", bytes);
            case AUTO_BINARY -> autoFormatSize(bytes, 1024.0, BINARY_UNITS);
            case AUTO_SI -> autoFormatSize(bytes, 1000.0, SI_UNITS);
        };
    }

    /**
     * Always-human-readable size for drive/free-space displays, where a raw byte count
     * (hundreds of gigabytes as a 12-digit number) is unreadable even when the user prefers
     * {@link FileSizeFormat#BYTES} for per-file sizes.
     */
    public static String formatSizeAuto(long bytes, FileSizeFormat format) {
        return formatSize(bytes, format == FileSizeFormat.AUTO_SI ? FileSizeFormat.AUTO_SI : FileSizeFormat.AUTO_BINARY);
    }

    private static String autoFormatSize(long bytes, double base, String[] units) {
        if (bytes < base) {
            return bytes + " " + units[0];
        }
        double size = bytes / base;
        int i = 1;
        while (size >= base && i < units.length - 1) {
            size /= base;
            i++;
        }
        return String.format(Locale.ROOT, "%.1f %s", size, units[i]);
    }
}
