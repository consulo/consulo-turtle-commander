package consulo.turtlecommander.ui;

import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.ActionPopupMenu;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import javax.swing.text.JTextComponent;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.util.function.BooleanSupplier;

/** Standard cut/copy/paste/delete/select-all popup for a plain text field. */
public final class TextFieldContextMenuUtil {
    private TextFieldContextMenuUtil() {
    }

    public static void installStandardContextMenu(JTextComponent field) {
        DefaultActionGroup group = new DefaultActionGroup();

        // Cut / Paste / Delete mutate, so they stay disabled on read-only fields. The underlying
        // JTextComponent calls are already no-ops there, but the menu would still light up.
        group.add(action("Cut", PlatformIconGroup.actionsMenu_cut(), field::cut,
            () -> field.isEditable() && field.getSelectedText() != null));
        group.add(action("Copy", PlatformIconGroup.actionsCopy(), field::copy,
            () -> field.getSelectedText() != null));
        group.add(action("Paste", PlatformIconGroup.actionsMenu_paste(), field::paste,
            () -> field.isEditable() && clipboardHasText()));
        group.add(AnSeparator.create());
        group.add(action("Delete", PlatformIconGroup.actionsGc(), () -> field.replaceSelection(""),
            () -> field.isEditable() && field.getSelectedText() != null));
        group.add(AnSeparator.create());
        group.add(action("Select All", null, field::selectAll, () -> !field.getText().isEmpty()));

        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, group);
        field.setComponentPopupMenu(popupMenu.getComponent());
    }

    private static boolean clipboardHasText() {
        try {
            return Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(DataFlavor.stringFlavor);
        }
        catch (Exception e) {
            return false;
        }
    }

    private static AnAction action(String text, @Nullable Image icon, Runnable action, BooleanSupplier isEnabled) {
        return new TextFieldAction(LocalizeValue.localizeTODO(text), icon, action, isEnabled);
    }

    /**
     * Implements {@link AnActionWithSyncUpdate} because these items read live Swing state
     * (selection, editability, clipboard) and must be evaluated synchronously on the UI thread,
     * rather than through Consulo's default asynchronous update path.
     */
    private static final class TextFieldAction extends AnAction implements AnActionWithSyncUpdate {
        private final Runnable myAction;
        private final BooleanSupplier myIsEnabled;

        TextFieldAction(LocalizeValue text, @Nullable Image icon, Runnable action, BooleanSupplier isEnabled) {
            super(text, LocalizeValue.empty(), icon);
            myAction = action;
            myIsEnabled = isEnabled;
        }

        @RequiredUIAccess
        @Override
        public void actionPerformed(AnActionEvent e) {
            myAction.run();
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(myIsEnabled.getAsBoolean());
        }
    }
}
