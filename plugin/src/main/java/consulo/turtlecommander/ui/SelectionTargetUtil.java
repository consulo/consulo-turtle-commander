package consulo.turtlecommander.ui;

import org.jspecify.annotations.Nullable;

import java.util.function.IntFunction;

/**
 * Picks which row a view should select after re-listing a directory.
 *
 * <p>All four views (table, list, thumbnail, tree) apply the same rule, so it lives here once:
 * prefer an explicit name, else the cursor position saved for this directory, else the first row.
 */
public final class SelectionTargetUtil {
    /** No row to select — the view is empty. */
    public static final int NONE = -1;

    private SelectionTargetUtil() {
    }

    /**
     * @param count      number of rows currently in the view
     * @param nameAt     the entry name at a given index
     * @param selectName name to select, or null to fall through to {@code savedCursor}
     * @param savedCursor previously remembered row, clamped to the last row — a saved index can
     *                    point past the end after the user deleted the final entry
     */
    public static int resolveTargetIndex(int count, IntFunction<@Nullable String> nameAt,
                                         @Nullable String selectName, @Nullable Integer savedCursor) {
        if (count <= 0) {
            return NONE;
        }

        if (selectName != null) {
            for (int i = 0; i < count; i++) {
                if (selectName.equals(nameAt.apply(i))) {
                    return i;
                }
            }
            // An explicit name that no longer exists falls back to the first row rather than to a
            // stale cursor, which would land somewhere unrelated to what the caller asked for.
            return 0;
        }

        if (savedCursor != null) {
            return Math.min(savedCursor, count - 1);
        }
        return 0;
    }
}
