package consulo.turtlecommander.ui;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.util.FileUtils;
import org.jspecify.annotations.Nullable;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

/**
 * The quick-filter glob, with its compiled matcher cached across keystrokes.
 *
 * <p>Caching matters because the filter re-runs on every character typed, and recompiling a
 * {@code glob:} PathMatcher per keystroke over a large listing is wasteful.
 */
public class QuickFilterMatcher {
    /** A filtered listing plus whether the pattern failed to compile. */
    public record Result(List<FileEntry> entries, boolean invalidPattern) {
    }

    private @Nullable String myCachedGlob;
    private @Nullable PathMatcher myCachedMatcher;

    /**
     * Filters {@code all} by {@code pattern}. An empty pattern passes everything through. The
     * parent ".." row always survives, so the user can still navigate up while filtering.
     *
     * <p>An invalid glob returns the unfiltered list with {@code invalidPattern} set, rather than
     * freezing the view on the previous filter's output — the caller flags the field so the user
     * can see why nothing changed.
     */
    public Result filter(List<FileEntry> all, String pattern) {
        String trimmed = pattern.trim();
        if (trimmed.isEmpty()) {
            invalidate();
            return new Result(all, false);
        }

        String glob = FileUtils.wrapAsSubstringGlobIfPlain(trimmed);
        PathMatcher matcher = matcherFor(glob);
        if (matcher == null) {
            return new Result(all, true);
        }

        List<FileEntry> filtered = new ArrayList<>();
        for (FileEntry entry : all) {
            if (entry.parentLink() || matcher.matches(Path.of(entry.name()))) {
                filtered.add(entry);
            }
        }
        return new Result(filtered, false);
    }

    public void invalidate() {
        myCachedGlob = null;
        myCachedMatcher = null;
    }

    private @Nullable PathMatcher matcherFor(String glob) {
        if (glob.equals(myCachedGlob) && myCachedMatcher != null) {
            return myCachedMatcher;
        }
        try {
            PathMatcher compiled = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            myCachedGlob = glob;
            myCachedMatcher = compiled;
            return compiled;
        }
        catch (Exception e) {
            // Drop the stale cache so a later valid pattern doesn't reuse it.
            invalidate();
            return null;
        }
    }
}
