package consulo.turtlecommander.ui;

import consulo.turtlecommander.settings.ColumnConfig;
import consulo.turtlecommander.settings.ComponentStyle;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.ui.ex.awt.table.JBTable;
import org.jspecify.annotations.Nullable;

import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Column identity and default sizing for the table view. */
public final class FileTabColumnUtil {
    /** Settings-facing column id to the model index it maps to. Iteration order is display order. */
    public static final Map<String, Integer> COLUMN_NAME_TO_MODEL_INDEX = buildColumnIndex();

    private static final Map<String, Integer> DEFAULT_COLUMN_WIDTHS = buildDefaultWidths();
    private static final int FALLBACK_WIDTH = 80;

    private FileTabColumnUtil() {
    }

    public static void applyDefaultColumnWidths(JBTable table) {
        TableColumnModel cm = table.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            TableColumn tc = cm.getColumn(i);
            Integer width = DEFAULT_COLUMN_WIDTHS.get(columnNameOf(tc.getModelIndex()));
            int applied = width != null ? width : FALLBACK_WIDTH;
            tc.setPreferredWidth(applied);
            tc.setWidth(applied);
        }
    }

    /**
     * Applies the user's column configuration: per-column renderers and styles, then hides the
     * columns turned off in settings and reorders the survivors to match.
     *
     * <p>Renderers are installed before anything is removed, so a column hidden now and shown again
     * later already carries the right renderer.
     */
    public static void applyColumnConfig(FileTab tab) {
        JBTable table = tab.getTable();
        List<ColumnConfig> columns = TurtleCommanderSettings.getInstance().getEffectiveColumns();

        Map<Integer, ComponentStyle> stylesByModelIndex = new HashMap<>();
        List<Integer> desiredOrder = new ArrayList<>();
        Set<Integer> visibleModelIndices = new HashSet<>();
        for (ColumnConfig column : columns) {
            Integer modelIndex = COLUMN_NAME_TO_MODEL_INDEX.get(column.id);
            if (modelIndex == null) {
                continue;
            }
            if (column.visible) {
                visibleModelIndices.add(modelIndex);
                desiredOrder.add(modelIndex);
            }
            if (!column.style.isDefault()) {
                stylesByModelIndex.put(modelIndex, column.style);
            }
        }

        TableColumnModel cm = table.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            TableColumn tc = cm.getColumn(i);
            ComponentStyle style = stylesByModelIndex.get(tc.getModelIndex());
            tc.setCellRenderer(tc.getModelIndex() == FileTableModel.COL_NAME
                ? new StyledFileNameCellRenderer(tab, style)
                : new StyledDisplayValueRenderer(tab, style));
        }
        applyDefaultColumnWidths(table);

        for (int i = cm.getColumnCount() - 1; i >= 0; i--) {
            TableColumn tc = cm.getColumn(i);
            if (!visibleModelIndices.contains(tc.getModelIndex())) {
                table.removeColumn(tc);
            }
        }

        for (int targetIdx = 0; targetIdx < desiredOrder.size(); targetIdx++) {
            int currentIdx = viewIndexOf(cm, desiredOrder.get(targetIdx));
            if (currentIdx >= 0 && currentIdx != targetIdx) {
                cm.moveColumn(currentIdx, targetIdx);
            }
        }
    }

    /**
     * Rebuilds the column model from scratch and re-applies the configuration, preserving the
     * user's current order and widths for the columns that survive. Used when the visible set
     * changes, since a hidden column has to be re-added before it can be shown.
     */
    public static void reapplyColumnConfig(FileTab tab) {
        JBTable table = tab.getTable();
        TableColumnModel cm = table.getColumnModel();

        List<Integer> savedOrder = new ArrayList<>();
        Map<Integer, Integer> savedWidths = new HashMap<>();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            TableColumn tc = cm.getColumn(i);
            savedOrder.add(tc.getModelIndex());
            savedWidths.put(tc.getModelIndex(), tc.getWidth());
        }

        while (cm.getColumnCount() > 0) {
            table.removeColumn(cm.getColumn(0));
        }
        for (int i = 0; i < tab.getTableModel().getColumnCount(); i++) {
            TableColumn tc = new TableColumn(i);
            tc.setHeaderValue(tab.getTableModel().getColumnName(i));
            table.addColumn(tc);
        }
        applyColumnConfig(tab);

        if (!savedOrder.isEmpty()) {
            restoreOrderAndWidths(table, savedOrder, savedWidths);
        }
        tab.assignNameEditor();
    }

    /** Keeps the columns the user had, in their order, and appends any newly-shown ones. */
    private static void restoreOrderAndWidths(JBTable table, List<Integer> savedOrder,
                                              Map<Integer, Integer> savedWidths) {
        TableColumnModel cm = table.getColumnModel();
        List<Integer> visibleModelIndices = new ArrayList<>();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            visibleModelIndices.add(cm.getColumn(i).getModelIndex());
        }

        List<Integer> targetOrder = new ArrayList<>();
        for (int modelIndex : savedOrder) {
            if (visibleModelIndices.contains(modelIndex)) {
                targetOrder.add(modelIndex);
            }
        }
        for (int modelIndex : visibleModelIndices) {
            if (!savedOrder.contains(modelIndex)) {
                targetOrder.add(modelIndex);
            }
        }

        if (targetOrder.size() == cm.getColumnCount()) {
            for (int targetIdx = 0; targetIdx < targetOrder.size(); targetIdx++) {
                int currentIdx = viewIndexOf(cm, targetOrder.get(targetIdx));
                if (currentIdx >= 0 && currentIdx != targetIdx) {
                    cm.moveColumn(currentIdx, targetIdx);
                }
            }
        }

        for (int i = 0; i < cm.getColumnCount(); i++) {
            TableColumn tc = cm.getColumn(i);
            Integer width = savedWidths.get(tc.getModelIndex());
            if (width != null) {
                tc.setPreferredWidth(width);
                tc.setWidth(width);
            }
        }
    }

    private static int viewIndexOf(TableColumnModel cm, int modelIndex) {
        for (int i = 0; i < cm.getColumnCount(); i++) {
            if (cm.getColumn(i).getModelIndex() == modelIndex) {
                return i;
            }
        }
        return -1;
    }

    /** The settings-facing id for a model index, or null when the index isn't a known column. */
    public static @Nullable String columnNameOf(int modelIndex) {
        for (Map.Entry<String, Integer> entry : COLUMN_NAME_TO_MODEL_INDEX.entrySet()) {
            if (entry.getValue() == modelIndex) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Map<String, Integer> buildColumnIndex() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("Name", FileTableModel.COL_NAME);
        map.put("Ext", FileTableModel.COL_EXT);
        map.put("Size", FileTableModel.COL_SIZE);
        map.put("Date Created", FileTableModel.COL_CREATED);
        map.put("Date Modified", FileTableModel.COL_DATE);
        map.put("User", FileTableModel.COL_OWNER);
        map.put("Group", FileTableModel.COL_GROUP);
        map.put("Permissions", FileTableModel.COL_PERMS);
        map.put("Inode", FileTableModel.COL_INODE);
        map.put("Links", FileTableModel.COL_LINKS);
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, Integer> buildDefaultWidths() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("Name", 275);
        map.put("Ext", 80);
        map.put("Size", 100);
        map.put("Date Created", 150);
        map.put("Date Modified", 150);
        map.put("User", 100);
        map.put("Group", 100);
        map.put("Permissions", 100);
        map.put("Inode", 120);
        map.put("Links", 70);
        return Collections.unmodifiableMap(map);
    }
}
