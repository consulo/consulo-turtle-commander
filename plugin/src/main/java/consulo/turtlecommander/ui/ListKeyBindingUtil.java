package consulo.turtlecommander.ui;

import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.event.details.KeyCode;
import consulo.ui.event.details.KeyStroke;
import consulo.ui.event.details.ModifiedInputDetails.Modifier;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.KeyboardShortcut;

import javax.swing.JComponent;
import javax.swing.JList;

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
        register(list, KeyStroke.pressed(KeyCode.HOME), () -> {
            if (list.getModel().getSize() > 0) {
                selectAndScroll(list, 0);
            }
        });

        register(list, KeyStroke.pressed(KeyCode.END), () -> {
            int last = list.getModel().getSize() - 1;
            if (last >= 0) {
                selectAndScroll(list, last);
            }
        });

        register(list, KeyStroke.pressed(KeyCode.HOME, Modifier.SHIFT), () -> {
            if (list.getModel().getSize() > 0) {
                extendSelectionTo(list, 0);
            }
        });

        register(list, KeyStroke.pressed(KeyCode.END, Modifier.SHIFT), () -> {
            int last = list.getModel().getSize() - 1;
            if (last >= 0) {
                extendSelectionTo(list, last);
            }
        });
    }

    /** Space toggles the marked state of the current row; Insert toggles it and moves down. */
    public static void installToggleSelectionBinding(JComponent component, Runnable onToggle,
                                                     Runnable onToggleAndMoveDown) {
        installShortcut(component, KeyStroke.pressed(KeyCode.SPACE), onToggle);
        installShortcut(component, KeyStroke.pressed(KeyCode.INSERT), onToggleAndMoveDown);
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
