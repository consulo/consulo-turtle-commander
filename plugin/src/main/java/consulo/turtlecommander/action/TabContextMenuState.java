package consulo.turtlecommander.action;

import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.ui.ex.action.AnActionEvent;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;

/**
 * Which tab the tab-strip context menu was invoked on. Mirrors {@link FileContextMenuState}: the
 * menu's actions come from a shared group and so read the target from here rather than from
 * parameters, and every read is gated on the action's {@code place} so a value can't leak into a
 * keyboard or Find Action dispatch. The panel is held weakly so it isn't pinned between menus.
 */
public final class TabContextMenuState {
    private static int ourClickedTabIndex = -1;
    private static WeakReference<FileManagerPanel> ourClickedPanel = new WeakReference<>(null);

    private TabContextMenuState() {
    }

    private static boolean isContextMenu(AnActionEvent e) {
        return ActionTargets.TAB_CONTEXT_PLACE.equals(e.getPlace());
    }

    public static int getClickedTabIndex(AnActionEvent e) {
        return isContextMenu(e) ? ourClickedTabIndex : -1;
    }

    public static void setClickedTabIndex(int index) {
        ourClickedTabIndex = index;
    }

    public static @Nullable FileManagerPanel getClickedPanel(AnActionEvent e) {
        return isContextMenu(e) ? ourClickedPanel.get() : null;
    }

    public static void setClickedPanel(@Nullable FileManagerPanel panel) {
        ourClickedPanel = new WeakReference<>(panel);
    }

    public static void clear() {
        ourClickedTabIndex = -1;
        ourClickedPanel = new WeakReference<>(null);
    }
}
