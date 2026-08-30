package consulo.turtlecommander.ui;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.settings.ComponentStyle;
import org.jspecify.annotations.Nullable;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.awt.Font;

/**
 * Every column except Name: renders the model's display string with the same mark and cursor
 * backgrounds the name column uses, so a marked row reads as one continuous band.
 */
public class StyledDisplayValueRenderer extends DefaultTableCellRenderer {
    private final FileTab myTab;
    private final @Nullable ComponentStyle myStyle;

    public StyledDisplayValueRenderer(FileTab tab, @Nullable ComponentStyle style) {
        myTab = tab;
        myStyle = style;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, @Nullable Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        int modelRow = table.convertRowIndexToModel(row);
        int modelCol = table.convertColumnIndexToModel(column);

        String displayValue = myTab.getTableModel().getDisplayValue(modelRow, modelCol);
        FileEntry entry = myTab.getTableModel().getEntryAt(modelRow);
        boolean marked = entry != null && myTab.getMarkedPaths().contains(entry.path());
        boolean cursor = row == table.getSelectionModel().getLeadSelectionIndex();

        // hasFocus is forced false: the cursor row is drawn by us, and letting the LAF paint its
        // focus border on top would double up on the highlight.
        Component component = super.getTableCellRendererComponent(table, displayValue, isSelected, false, row, column);

        if (marked && cursor) {
            setBackground(MarkColors.MARK_CURSOR_BACKGROUND);
            setForeground(table.getForeground());
        }
        else if (marked) {
            setBackground(MarkColors.MARK_BACKGROUND);
            setForeground(table.getForeground());
        }
        else if (cursor) {
            boolean focused = table.hasFocus();
            setBackground(focused ? table.getSelectionBackground() : MarkColors.inactiveSelectionBackground(table));
            setForeground(focused ? table.getSelectionForeground() : MarkColors.inactiveSelectionForeground(table));
        }
        else if (isSelected && !table.hasFocus()) {
            setBackground(MarkColors.inactiveSelectionBackground(table));
            setForeground(MarkColors.inactiveSelectionForeground(table));
        }
        else if (!isSelected) {
            setBackground(table.getBackground());
            setForeground(myStyle != null && myStyle.parsedFontColor() != null
                ? myStyle.parsedFontColor()
                : table.getForeground());
        }

        if (myStyle != null) {
            Font styled = myStyle.getFont(table.getFont());
            if (styled != null) {
                setFont(styled);
            }
        }
        return component;
    }
}
