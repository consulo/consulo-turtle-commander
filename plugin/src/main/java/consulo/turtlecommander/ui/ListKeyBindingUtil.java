package consulo.turtlecommander.ui;

import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.KeyboardShortcut;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Keyboard bindings shared by the list-backed views.
 *
 * <p>These are registered as platform actions rather than Swing key bindings so they take priority
 * over the global keymap — a bare Space or Home inside the panel must not fall through to whatever
 * the IDE binds it to.
 */
public final class ListKeyBindingUtil {
    private ListKeyBindingUtil() {
    }

    /** Home/End jump to the first/last row; Shift+Home/End extend the selection from the anchor. */
    public static void installHomeEndBindings(JList<?> list) {
        register(list, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), () -> {
            if (list.getModel().getSize() > 0) {
                selectAndScroll(list, 0);
            }
        });

        register(list, KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), () -> {
            int last = list.getModel().getSize() - 1;
            if (last >= 0) {
                selectAndScroll(list, last);
            }
        });

        register(list, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, InputEvent.SHIFT_DOWN_MASK), () -> {
            if (list.getModel().getSize() > 0) {
                extendSelectionTo(list, 0);
            }
        });

        register(list, KeyStroke.getKeyStroke(KeyEvent.VK_END, InputEvent.SHIFT_DOWN_MASK), () -> {
            int last = list.getModel().getSize() - 1;
            if (last >= 0) {
                extendSelectionTo(list, last);
            }
        });
    }

    /** Space toggles the marked state of the current row; Insert toggles it and moves down. */
    public static void installToggleSelectionBinding(JComponent component, Runnable onToggle,
                                                     Runnable onToggleAndMoveDown) {
        installShortcut(component, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), onToggle);
        installShortcut(component, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), onToggleAndMoveDown);
    }

    private static void selectAndScroll(JList<?> list, int index) {
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
    }

    private static void extendSelectionTo(JList<?> list, int index) {
        int anchor = Math.max(0, list.getSelectionModel().getAnchorSelectionIndex());
        list.getSelectionModel().setSelectionInterval(anchor, index);
        list.ensureIndexIsVisible(index);
    }

    private static void register(JComponent component, KeyStroke keyStroke, Runnable action) {
        new SimpleAction(action).registerCustomShortcutSet(new CustomShortcutSet(keyStroke), component);
    }

    /** Binds one keystroke to {@code component} as a component-local platform action. */
    public static void installShortcut(JComponent component, KeyStroke keyStroke, Runnable action) {
        new SimpleAction(action).registerCustomShortcutSet(
            new CustomShortcutSet(new KeyboardShortcut(keyStroke, null)), component);
    }

    private static final class SimpleAction extends DumbAwareAction {
        private final Runnable myAction;

        SimpleAction(Runnable action) {
            myAction = action;
        }

        @RequiredUIAccess
        @Override
        public void actionPerformed(AnActionEvent e) {
            myAction.run();
        }
    }
}
