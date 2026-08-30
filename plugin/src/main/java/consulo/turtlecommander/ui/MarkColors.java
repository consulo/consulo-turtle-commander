package consulo.turtlecommander.ui;

import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.ui.ex.JBColor;
import org.jspecify.annotations.Nullable;

import javax.swing.JTable;
import java.awt.Color;

/**
 * Row backgrounds for marked entries and for a selection in an unfocused panel.
 *
 * <p>Persistent marks (Insert/Space) get their own warm amber so they stay visually distinct from
 * the cursor row's selection: theme blue is reserved for the cursor-only row, and the
 * cursor-on-a-marked-row combination uses a deeper saturated orange.
 */
public final class MarkColors {
    public static final JBColor MARK_BACKGROUND = new JBColor(new Color(0xFFD180), new Color(0x6B4A1F));
    public static final JBColor MARK_CURSOR_BACKGROUND = new JBColor(new Color(0xFF8C42), new Color(0xA86A20));

    private MarkColors() {
    }

    /**
     * The selection tint for an unfocused panel: the user's configured colour when set, otherwise
     * the active selection blended halfway toward the table background so the inactive panel reads
     * as secondary.
     */
    public static Color inactiveSelectionBackground(JTable table) {
        @Nullable Color custom = TurtleCommanderSettings.getInstance()
            .getState().styles.panelStyle.parsedSelectedColor();
        if (custom != null) {
            return custom;
        }

        Color active = table.getSelectionBackground();
        Color background = table.getBackground();
        Color blended = new Color(
            blend(active.getRed(), background.getRed()),
            blend(active.getGreen(), background.getGreen()),
            blend(active.getBlue(), background.getBlue()));
        return new JBColor(blended, blended);
    }

    public static Color inactiveSelectionForeground(JTable table) {
        return table.getForeground();
    }

    private static int blend(int a, int b) {
        return (a + b) / 2;
    }
}
