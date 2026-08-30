package consulo.turtlecommander.action;

import consulo.turtlecommander.model.FileEntry;
import consulo.ui.ex.action.AnActionEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What the search-results context menu was invoked on. Reads are gated on the action's
 * {@code place}, so a stale value can't leak into a keyboard or Find Action dispatch.
 */
public final class SearchContextMenuState {
    private static @Nullable FileEntry ourClickedEntry;
    private static List<FileEntry> ourSelectedEntries = List.of();

    private SearchContextMenuState() {
    }

    private static boolean isContextMenu(AnActionEvent e) {
        return ActionTargets.SEARCH_CONTEXT_PLACE.equals(e.getPlace());
    }

    public static @Nullable FileEntry getClickedEntry(AnActionEvent e) {
        return isContextMenu(e) ? ourClickedEntry : null;
    }

    public static void setClickedEntry(@Nullable FileEntry entry) {
        ourClickedEntry = entry;
    }

    public static List<FileEntry> getSelectedEntries(AnActionEvent e) {
        return isContextMenu(e) ? ourSelectedEntries : List.of();
    }

    public static void setSelectedEntries(List<FileEntry> entries) {
        ourSelectedEntries = List.copyOf(entries);
    }

    public static void clear() {
        ourClickedEntry = null;
        ourSelectedEntries = List.of();
    }
}
