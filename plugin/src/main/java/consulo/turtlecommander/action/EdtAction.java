package consulo.turtlecommander.action;

import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import javax.swing.KeyStroke;

/**
 * Base class for the plugin's actions. All of them resolve their enabled/visible state from live
 * Swing state — the focused tab, the right-clicked entry — which must be read on the UI thread.
 *
 * <p>IntelliJ expressed that by overriding {@code getActionUpdateThread()} to return EDT. Consulo
 * has no such method: synchronous update is opted into by implementing
 * {@link AnActionWithSyncUpdate}, so that lives here instead and every action inherits it along
 * with {@link DumbAware}.
 */
public abstract class EdtAction extends AnAction implements DumbAware, AnActionWithSyncUpdate {
    protected EdtAction() {
        super();
    }

    protected EdtAction(LocalizeValue text) {
        super(text);
    }

    protected EdtAction(LocalizeValue text, LocalizeValue description, @Nullable Image icon) {
        super(text, description, icon);
    }

    /**
     * Always available. {@link AnActionWithSyncUpdate} makes {@code update} abstract, so an action
     * with no enablement rules of its own still needs this — overridden by the subclasses whose
     * availability depends on the focused tab or the clicked entry.
     */
    @Override
    public void update(AnActionEvent e) {
    }

    /**
     * Applies the keystroke this action carries until the user rebinds it.
     *
     * <p>Consulo's {@code @ActionImpl} has no attribute for declaring a fresh binding — only
     * {@code shortcutFrom}, which borrows another action's — so an action sets its own default
     * here, which is the pattern the platform's own actions use.
     *
     * @param shortcut a {@link KeyStroke#getKeyStroke(String)} spec, e.g. {@code "shift F6"}
     */
    protected final void setDefaultShortcut(@Nullable String shortcut) {
        if (shortcut == null) {
            return;
        }
        KeyStroke keyStroke = KeyStroke.getKeyStroke(shortcut);
        if (keyStroke != null) {
            setShortcutSet(new CustomShortcutSet(keyStroke));
        }
    }
}
