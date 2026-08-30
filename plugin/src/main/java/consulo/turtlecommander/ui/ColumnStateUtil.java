package consulo.turtlecommander.ui;

import consulo.ui.ex.awt.table.JBTable;

import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.util.ArrayList;
import java.util.List;

/** Captures and restores the table's per-tab column widths and ordering. */
public final class ColumnStateUtil {
    /** Widths in view order, and the model index of each view position. */
    public record ColumnState(List<Integer> widths, List<Integer> order) {
        public static final ColumnState EMPTY = new ColumnState(List.of(), List.of());

        public boolean isEmpty() {
            return widths.isEmpty() && order.isEmpty();
        }
    }

    private ColumnStateUtil() {
    }

    public static ColumnState capture(JBTable table) {
        TableColumnModel cm = table.getColumnModel();
        List<Integer> widths = new ArrayList<>(cm.getColumnCount());
        List<Integer> order = new ArrayList<>(cm.getColumnCount());

        for (int i = 0; i < cm.getColumnCount(); i++) {
            TableColumn column = cm.getColumn(i);
            widths.add(column.getWidth());
            order.add(column.getModelIndex());
            // Pin the preferred width to the actual one so a later layout pass doesn't undo a
            // width the user dragged.
            column.setPreferredWidth(column.getWidth());
        }
        return new ColumnState(widths, order);
    }

    /**
     * Applies a captured state. Both lists are ignored unless their size matches the current
     * column count — a state saved when a different set of columns was visible can't be mapped
     * onto the current model, and applying it partially would scramble the layout.
     */
    public static void apply(JBTable table, List<Integer> widths, List<Integer> order) {
        TableColumnModel cm = table.getColumnModel();

        if (order.size() == cm.getColumnCount()) {
            for (int targetIdx = 0; targetIdx < order.size(); targetIdx++) {
                int wantedModelIdx = order.get(targetIdx);
                int currentViewIdx = viewIndexOf(cm, wantedModelIdx);
                if (currentViewIdx >= 0 && currentViewIdx != targetIdx) {
                    cm.moveColumn(currentViewIdx, targetIdx);
                }
            }
        }

        if (widths.size() == cm.getColumnCount()) {
            for (int i = 0; i < cm.getColumnCount(); i++) {
                TableColumn column = cm.getColumn(i);
                column.setPreferredWidth(widths.get(i));
                column.setWidth(widths.get(i));
            }
        }
    }

    /** Serialised form used by the persisted tab state: comma-separated integers. */
    public static String encode(List<Integer> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    /** Inverse of {@link #encode}; unparseable entries yield an empty list rather than throwing. */
    public static List<Integer> decode(String encoded) {
        if (encoded.isBlank()) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>();
        for (String part : encoded.split(",")) {
            try {
                values.add(Integer.valueOf(part.trim()));
            }
            catch (NumberFormatException e) {
                return List.of();
            }
        }
        return values;
    }

    private static int viewIndexOf(TableColumnModel cm, int modelIndex) {
        for (int i = 0; i < cm.getColumnCount(); i++) {
            if (cm.getColumn(i).getModelIndex() == modelIndex) {
                return i;
            }
        }
        return -1;
    }
}
