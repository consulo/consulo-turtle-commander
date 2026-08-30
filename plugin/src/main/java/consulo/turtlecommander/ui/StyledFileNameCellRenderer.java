package consulo.turtlecommander.ui;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.settings.ComponentStyle;
import consulo.turtlecommander.settings.ResolvedStyle;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.ColoredTableCellRenderer;
import consulo.ui.ex.awt.speedSearch.SpeedSearchUtil;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import javax.swing.JTable;
import java.awt.Color;
import java.awt.Font;

/**
 * The Name column: file-type icon with rule overlays, rule-driven colours, mark and cursor
 * backgrounds, and speed-search highlighting.
 */
public class StyledFileNameCellRenderer extends ColoredTableCellRenderer {
    private final FileTab myTab;
    private final @Nullable ComponentStyle myStyle;

    public StyledFileNameCellRenderer(FileTab tab, @Nullable ComponentStyle style) {
        myTab = tab;
        myStyle = style;
        // Paint our own background, including the inactive-selection tint.
        setOpaque(true);
    }

    @Override
    protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus,
                                         int row, int column) {
        FileEntry entry = myTab.getTableModel().getEntryAt(table.convertRowIndexToModel(row));
        boolean marked = entry != null && myTab.getMarkedPaths().contains(entry.path());
        boolean cursor = row == table.getSelectionModel().getLeadSelectionIndex();
        boolean highlighting = myTab.isEnableFileNameHighlighting();

        ResolvedStyle resolved = entry != null
            ? FileStyleUtil.resolvedStyleFor(entry, highlighting)
            : ResolvedStyle.EMPTY;

        Image icon = entry != null ? FileEntryIconUtil.imageFor(entry, highlighting, resolved) : null;
        setIcon(icon);
        setToolTipText(tooltipFor(entry));

        setBackground(backgroundFor(table, resolved, marked, cursor, selected));

        Color fg = foregroundFor(table, resolved, marked, cursor, selected);
        int fontStyle = myStyle != null && myStyle.getFont(table.getFont()) != null
            ? myStyle.getFont(table.getFont()).getStyle()
            : Font.PLAIN;

        SimpleTextAttributes attributes =
            FileStyleUtil.nameTextAttributes(fontStyle, fg, resolved.strikethrough());
        append(value != null ? value.toString() : "", attributes);

        if (myStyle != null) {
            Font styled = myStyle.getFont(table.getFont());
            if (styled != null) {
                setFont(styled);
            }
        }

        SpeedSearchUtil.applySpeedSearchHighlighting(table, this, true, selected);

        // ColoredTableCellRenderer may install a focus border in super; clear it so the LAF's
        // focus rectangle doesn't fight the cursor-row background.
        setBorder(null);
    }

    private static @Nullable String tooltipFor(@Nullable FileEntry entry) {
        if (entry == null) {
            return null;
        }
        if (entry.symbolicLink() && entry.linkTarget() != null) {
            return "\u2192 " + entry.linkTarget() + (entry.brokenSymlink() ? " (broken)" : "");
        }
        return entry.namedPipe() ? "Named pipe (FIFO)" : null;
    }

    private Color backgroundFor(JTable table, ResolvedStyle resolved, boolean marked, boolean cursor,
                                boolean selected) {
        if (marked && cursor) {
            return MarkColors.MARK_CURSOR_BACKGROUND;
        }
        if (marked) {
            return MarkColors.MARK_BACKGROUND;
        }
        if (cursor) {
            return table.hasFocus() ? table.getSelectionBackground() : MarkColors.inactiveSelectionBackground(table);
        }
        if (selected && !table.hasFocus()) {
            return MarkColors.inactiveSelectionBackground(table);
        }
        if (!selected) {
            Color ruleBackground = FileStyleUtil.backgroundColorOf(resolved);
            return ruleBackground != null ? ruleBackground : table.getBackground();
        }
        return table.getSelectionBackground();
    }

    private @Nullable Color foregroundFor(JTable table, ResolvedStyle resolved, boolean marked, boolean cursor,
                                          boolean selected) {
        if (marked) {
            return table.getForeground();
        }
        if (cursor) {
            return table.hasFocus() ? table.getSelectionForeground() : MarkColors.inactiveSelectionForeground(table);
        }
        if (selected) {
            // Unfocused selection gets the muted foreground; a focused one keeps
            // ColoredTableCellRenderer's default selection colour.
            return table.hasFocus() ? null : MarkColors.inactiveSelectionForeground(table);
        }

        Color ruleColor = FileStyleUtil.fontColorOf(resolved);
        if (ruleColor != null) {
            return ruleColor;
        }
        Color styleColor = myStyle != null ? myStyle.parsedFontColor() : null;
        return styleColor != null ? styleColor : table.getForeground();
    }
}
