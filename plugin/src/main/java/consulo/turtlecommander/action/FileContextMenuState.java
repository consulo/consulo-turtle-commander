package consulo.turtlecommander.action;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.FileTab;
import consulo.ui.ex.action.AnActionEvent;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;

/**
 * What the file context menu was invoked on. The actions in the menu read this instead of taking
 * parameters, since the platform builds them from a shared action group.
 *
 * <p>Every read is gated on the action's {@code place}, so only a dispatch from the file context
 * menu sees the value. That is what keeps it from going stale: invoked from the keyboard or Find
 * Action, Properties or Copy Path would otherwise act on whatever was last right-clicked. The tab
 * is held weakly so a menu that is never reopened doesn't pin it, and with it the project.
 */
public final class FileContextMenuState {
    private static @Nullable FileEntry ourClickedEntry;
    private static WeakReference<FileTab> ourClickedTab = new WeakReference<>(null);

    private FileContextMenuState() {
    }

    private static boolean isContextMenu(AnActionEvent e) {
        return ActionTargets.FILE_CONTEXT_PLACE.equals(e.getPlace());
    }

    public static @Nullable FileEntry getClickedEntry(AnActionEvent e) {
        return isContextMenu(e) ? ourClickedEntry : null;
    }

    public static void setClickedEntry(@Nullable FileEntry entry) {
        ourClickedEntry = entry;
    }

    public static @Nullable FileTab getClickedTab(AnActionEvent e) {
        return isContextMenu(e) ? ourClickedTab.get() : null;
    }

    public static void setClickedTab(@Nullable FileTab tab) {
        ourClickedTab = new WeakReference<>(tab);
    }

    public static void clear() {
        ourClickedEntry = null;
        ourClickedTab = new WeakReference<>(null);
    }
}
