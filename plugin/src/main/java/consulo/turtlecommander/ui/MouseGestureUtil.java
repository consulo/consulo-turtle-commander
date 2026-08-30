package consulo.turtlecommander.ui;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.settings.TurtleCommanderSettings;

import javax.swing.SwingUtilities;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/** Shared predicates for distinguishing a plain click from a multi-select gesture. */
public final class MouseGestureUtil {
    private static final int MODIFIER_MASK = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK
        | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK;

    private static final int DEFAULT_MULTI_CLICK_INTERVAL = 400;
    private static final int MIN_MULTI_CLICK_INTERVAL = 200;
    private static final int MAX_MULTI_CLICK_INTERVAL = 800;

    private MouseGestureUtil() {
    }

    /**
     * A plain left-click with no modifiers. Ctrl/Shift/Alt/Meta clicks are multi-select gestures,
     * so they never arm a rename or open a directory.
     */
    public static boolean isPlainLeftClick(MouseEvent e) {
        return SwingUtilities.isLeftMouseButton(e) && (e.getModifiersEx() & MODIFIER_MASK) == 0;
    }

    /**
     * With "open directories with one click" enabled, a plain left-click on a row opens it — but
     * only when it is a directory or the parent link. Files keep their normal single-click
     * behaviour (selection, click-to-rename).
     *
     * @param onItem whether the click actually landed on a row rather than on empty space
     * @return true when the click was consumed by opening, so the caller skips its own handling
     */
    public static boolean maybeSingleClickOpenDirectory(FileTab tab, MouseEvent e, boolean onItem) {
        if (!onItem
            || !TurtleCommanderSettings.getInstance().getState().openDirectoriesWithSingleClick
            || !isPlainLeftClick(e)) {
            return false;
        }
        FileEntry entry = FileTabSelectionUtil.getSelectedEntry(tab);
        if (entry == null || !(entry.parentLink() || entry.directory())) {
            return false;
        }
        FileTabNavigationUtil.openSelectedEntry(tab);
        return true;
    }

    /**
     * The desktop's double-click interval, clamped to a sane range. Used to defer click-to-rename
     * long enough that a double-click (which opens the entry) cancels it first.
     */
    public static int doubleClickInterval() {
        Object value = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
        int interval = value instanceof Integer i ? i : DEFAULT_MULTI_CLICK_INTERVAL;
        return Math.max(MIN_MULTI_CLICK_INTERVAL, Math.min(MAX_MULTI_CLICK_INTERVAL, interval));
    }
}
