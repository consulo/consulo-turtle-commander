package consulo.turtlecommander.ui;

import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Where to land when a navigation target has disappeared — typically because the other panel
 * deleted an ancestor, or a tab was restored pointing at an unplugged drive.
 */
public final class NavigationFallbackUtil {
    private NavigationFallbackUtil() {
    }

    /** The nearest ancestor that still exists, or null when the path's root is gone too. */
    public static @Nullable Path nearestExistingAncestor(Path path) {
        return nearestExistingAncestor(path, NavigationFallbackUtil::isDirectoryQuietly);
    }

    /** Testable form: {@code exists} decides which candidates count as reachable. */
    public static @Nullable Path nearestExistingAncestor(Path path, Predicate<Path> exists) {
        for (Path current = path.getParent(); current != null; current = current.getParent()) {
            if (exists.test(current)) {
                return current;
            }
        }
        return null;
    }

    /**
     * Last resort when not even the root of a failed path survives: the user's home directory.
     * Null when home equals the failed path — so a failing navigation to home can't recurse into
     * itself — or when home is itself unavailable.
     */
    public static @Nullable Path homeDirectoryFallback(Path failed) {
        Path home;
        try {
            home = Path.of(System.getProperty("user.home"));
        }
        catch (Exception e) {
            return null;
        }
        if (home.equals(failed)) {
            return null;
        }
        return isDirectoryQuietly(home) ? home : null;
    }

    private static boolean isDirectoryQuietly(Path path) {
        try {
            return Files.isDirectory(path);
        }
        catch (Exception e) {
            return false;
        }
    }
}
