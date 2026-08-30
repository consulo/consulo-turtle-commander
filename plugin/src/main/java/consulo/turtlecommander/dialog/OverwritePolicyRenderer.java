package consulo.turtlecommander.dialog;

import consulo.turtlecommander.service.OverwritePolicy;
import org.jspecify.annotations.Nullable;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;

/**
 * User-facing labels for the {@link OverwritePolicy} combo in the copy / move / extract dialogs.
 * Centralised so all four call sites use identical wording.
 */
public class OverwritePolicyRenderer extends DefaultListCellRenderer {
    public static final OverwritePolicyRenderer INSTANCE = new OverwritePolicyRenderer();

    @Override
    public Component getListCellRendererComponent(@Nullable JList<?> list, @Nullable Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setText(label(value instanceof OverwritePolicy policy ? policy : null));
        return component;
    }

    public static String label(@Nullable OverwritePolicy policy) {
        if (policy == null) {
            return "";
        }
        return switch (policy) {
            case ASK -> "Ask for each existing file";
            case OVERWRITE_ALL -> "Overwrite all";
            case SKIP_ALL -> "Skip all existing";
            case IF_OLDER -> "Overwrite if older";
        };
    }
}
