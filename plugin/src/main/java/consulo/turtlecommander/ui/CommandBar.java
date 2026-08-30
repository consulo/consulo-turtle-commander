package consulo.turtlecommander.ui;

import consulo.dataContext.DataManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.turtlecommander.settings.ComponentStyle;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.KeyboardShortcut;
import consulo.ui.ex.action.Shortcut;
import consulo.ui.ex.action.util.ShortcutUtil;
import consulo.ui.ex.toolWindow.ToolWindow;
import org.jspecify.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Norton-style function-key bar along the bottom of the tool window.
 *
 * <p>Buttons are labelled with the live shortcut of the action they invoke, so rebinding a key in
 * the keymap relabels the bar. Only the buttons whose shortcut uses the currently-held modifier are
 * shown, which is how a single row of function keys covers several sets of commands.
 */
public class CommandBar extends JPanel {
    private static final int MODIFIER_MASK = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
        | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK;

    /** One button: the action supplying its label shortcut, its caption, and what it runs. */
    private record BarButton(String actionId, String label, JButton button) {
    }

    private final List<BarButton> myButtons = new ArrayList<>();
    private int myLastModifier;

    /** Look-and-feel values captured before styling, so clearing a style restores the default. */
    private @Nullable Color myDefaultBackground;

    public CommandBar(FileManagerPanel leftPanel, FileManagerPanel rightPanel, ToolWindow toolWindow,
                      Disposable disposable) {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        myDefaultBackground = getBackground();

        addButton("TurtleCommander.LeftDriveSelector", "Left", leftPanel::showDriveSelector);
        addButton("TurtleCommander.RightDriveSelector", "Right", rightPanel::showDriveSelector);
        addButton("TurtleCommander.SearchFiles", "Search",
            () -> invokeById("TurtleCommander.SearchFiles"));
        addButton("TurtleCommander.PreviousTab", "Prev",
            () -> activePanel(leftPanel, rightPanel).selectPreviousTab());
        addButton("TurtleCommander.NextTab", "Next",
            () -> activePanel(leftPanel, rightPanel).selectNextTab());
        addTabButton("TurtleCommander.ViewFile", "View", leftPanel, rightPanel,
            FileTabOperationUtil::viewSelectedFile);
        addTabButton("TurtleCommander.OpenInApp", "Open", leftPanel, rightPanel,
            FileTabOperationUtil::openSelectedInAssociatedApp);
        addTabButton("TurtleCommander.Refresh", "Refresh", leftPanel, rightPanel,
            tab -> FileTabNavigationUtil.refresh(tab, true));
        addTabButton("TurtleCommander.QuickFilter", "Filter", leftPanel, rightPanel,
            FileTab::showQuickFilter);
        addButton("TurtleCommander.NewTab", "Tab",
            () -> activePanel(leftPanel, rightPanel).openNewTab());
        addTabButton("TurtleCommander.CopyFiles", "Copy", leftPanel, rightPanel,
            FileTabOperationUtil::performCopy);
        addTabButton("TurtleCommander.MoveFiles", "Move", leftPanel, rightPanel,
            FileTabOperationUtil::performMove);
        addTabButton("TurtleCommander.CreateDirectory", "Mkdir", leftPanel, rightPanel,
            FileTabOperationUtil::performCreateDirectory);
        addTabButton("TurtleCommander.CreateLink", "Link", leftPanel, rightPanel,
            FileTabOperationUtil::performCreateLink);
        addButton("TurtleCommander.CloseTab", "Close", () -> {
            FileManagerPanel panel = activePanel(leftPanel, rightPanel);
            panel.closeTab(panel.getActiveTabIndex());
        });
        addTabButton("TurtleCommander.DeleteFiles", "Delete", leftPanel, rightPanel,
            tab -> FileTabOperationUtil.performDelete(tab, false));
        addTabButton("TurtleCommander.CreateFile", "New File", leftPanel, rightPanel,
            FileTabOperationUtil::performCreateFile);
        addTabButton("TurtleCommander.Rename", "Rename", leftPanel, rightPanel,
            FileTabOperationUtil::startRename);
        addButton("TurtleCommander.Quit", "Quit", toolWindow::hide);

        TurtleCommanderSettings.State settings = TurtleCommanderSettings.getInstance().getState();
        applyStyle(settings.styles.commandBarStyle, settings.styles.commandButtonStyle);
        updateBar(0);
        installModifierWatch(toolWindow, disposable);
    }

    /**
     * Which panel a bar button acts on. Checked through {@code hasFocusInPanel} so it covers all
     * four view modes and search-result tabs, and falls back to the right panel when neither has
     * focus — matching how the state service picks the active panel.
     */
    private static FileManagerPanel activePanel(FileManagerPanel leftPanel, FileManagerPanel rightPanel) {
        return leftPanel.hasFocusInPanel() ? leftPanel : rightPanel;
    }

    private void addButton(String actionId, String label, Runnable action) {
        JButton button = new JButton();
        button.setFocusable(false);
        button.addActionListener(e -> action.run());
        myButtons.add(new BarButton(actionId, label, button));
        add(button);
    }

    private void addTabButton(String actionId, String label, FileManagerPanel leftPanel,
                              FileManagerPanel rightPanel, java.util.function.Consumer<FileTab> action) {
        addButton(actionId, label, () -> {
            FileTab tab = activePanel(leftPanel, rightPanel).getActiveTab();
            if (tab != null) {
                action.accept(tab);
            }
        });
    }

    private void invokeById(String actionId) {
        AnAction action = ActionManager.getInstance().getAction(actionId);
        if (action == null) {
            return;
        }
        AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN,
            DataManager.getInstance().getDataContext(this));
        action.actionPerformed(event);
    }

    /** Rebuilds the row to show only the buttons bound to {@code activeModifier}. */
    public void updateBar(int activeModifier) {
        List<BarButton> matching = new ArrayList<>();
        for (BarButton button : myButtons) {
            if (shortcutOf(button.actionId()) != null && modifierOf(button.actionId()) == activeModifier) {
                matching.add(button);
            }
        }
        // A modifier nobody uses would blank the bar; fall back to the unmodified row.
        if (matching.isEmpty() && activeModifier != 0) {
            for (BarButton button : myButtons) {
                if (shortcutOf(button.actionId()) != null && modifierOf(button.actionId()) == 0) {
                    matching.add(button);
                }
            }
        }
        matching.sort(Comparator.comparingInt(button -> keyCodeOf(button.actionId())));

        removeAll();
        for (int i = 0; i < matching.size(); i++) {
            if (i > 0) {
                add(Box.createHorizontalStrut(2));
            }
            BarButton button = matching.get(i);
            String key = shortcutText(button.actionId());
            button.button().setText(key.isEmpty() ? button.label() : key + " " + button.label());
            add(button.button());
        }

        // Modifier rows are detached between key presses, so a settings change only restyles the
        // attached set — restyle here or a freshly-shown row appears with stale styling.
        TurtleCommanderSettings.State settings = TurtleCommanderSettings.getInstance().getState();
        applyStyle(settings.styles.commandBarStyle, settings.styles.commandButtonStyle);
        revalidate();
        repaint();
    }

    private static @Nullable KeyboardShortcut shortcutOf(String actionId) {
        AnAction action = ActionManager.getInstance().getAction(actionId);
        if (action == null) {
            return null;
        }
        for (Shortcut shortcut : action.getShortcutSet().getShortcuts()) {
            if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                return keyboardShortcut;
            }
        }
        return null;
    }

    private static int modifierOf(String actionId) {
        KeyboardShortcut shortcut = shortcutOf(actionId);
        return shortcut != null ? shortcut.getFirstKeyStroke().getModifiers() & MODIFIER_MASK : 0;
    }

    /** Orders the row by key code so F1..F12 read left to right regardless of registration order. */
    private static int keyCodeOf(String actionId) {
        KeyboardShortcut shortcut = shortcutOf(actionId);
        return shortcut != null ? shortcut.getFirstKeyStroke().getKeyCode() : Integer.MAX_VALUE;
    }

    private static String shortcutText(String actionId) {
        KeyboardShortcut shortcut = shortcutOf(actionId);
        if (shortcut == null) {
            return "";
        }
        KeyStroke keyStroke = shortcut.getFirstKeyStroke();
        return ShortcutUtil.getKeystrokeText(keyStroke);
    }

    /**
     * Swaps the row while a modifier is held, but only when focus is inside the tool window — so
     * holding Ctrl in the editor doesn't flip the bar's labels.
     */
    private void installModifierWatch(ToolWindow toolWindow, Disposable disposable) {
        KeyEventDispatcher dispatcher = e -> {
            if (!isShowing() || (e.getID() != KeyEvent.KEY_PRESSED && e.getID() != KeyEvent.KEY_RELEASED)) {
                return false;
            }
            switch (e.getKeyCode()) {
                case KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META -> {
                    Component focusOwner =
                        KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                    boolean insideToolWindow = focusOwner != null
                        && SwingUtilities.isDescendingFrom(focusOwner, toolWindow.getComponent());
                    applyModifier(insideToolWindow ? e.getModifiersEx() & MODIFIER_MASK : 0);
                }
                default -> {
                    // Only modifier keys change which row is shown.
                }
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
        Disposer.register(disposable, () ->
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher));
    }

    /** Rebuilding the row is not cheap, so skip it when the held modifier hasn't actually changed. */
    private void applyModifier(int modifier) {
        if (modifier != myLastModifier) {
            myLastModifier = modifier;
            updateBar(modifier);
        }
    }

    public void applyStyle(ComponentStyle barStyle, ComponentStyle buttonStyle) {
        if (myDefaultBackground == null) {
            myDefaultBackground = getBackground();
        }
        Color barBackground = barStyle.parsedBackgroundColor();
        setBackground(barBackground != null ? barBackground : myDefaultBackground);

        Font buttonFont = buttonStyle.getFont(null);
        Color buttonForeground = buttonStyle.parsedFontColor();
        Color buttonBackground = buttonStyle.parsedBackgroundColor();

        for (Component component : getComponents()) {
            if (!(component instanceof JButton button)) {
                continue;
            }
            if (buttonFont != null) {
                button.setFont(buttonFont);
            }
            if (buttonForeground != null) {
                button.setForeground(buttonForeground);
            }
            if (buttonBackground != null) {
                button.setBackground(buttonBackground);
            }
            // A custom background only shows through when the button paints its own fill.
            button.setContentAreaFilled(buttonBackground == null);
            button.setOpaque(buttonBackground != null);
        }
    }
}
