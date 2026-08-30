package consulo.turtlecommander.ui;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import javax.swing.SortOrder;
import javax.swing.table.TableRowSorter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Pins the parent ".." row at the top and optionally groups directories before files.
 *
 * <p>A model wrapper enriches every cell value with directory metadata, so each column's comparator
 * can detect directories without extra sort keys. The renderer still reads the real model, so
 * display is unaffected.
 */
public class ParentPinningRowSorter extends TableRowSorter<FileTableModel> {
    /** A cell value plus the directory/parent metadata its comparator needs. */
    record DirAwareValue(Object value, boolean directory, boolean parent) {
    }

    private final BooleanSupplier mySortWithDirectories;
    private final BooleanSupplier myNaturalNameSort;

    public ParentPinningRowSorter(FileTableModel fileModel) {
        this(fileModel,
            () -> TurtleCommanderSettings.getInstance().getState().sortWithDirectories,
            () -> TurtleCommanderSettings.getInstance().getState().naturalNameSort);
    }

    public ParentPinningRowSorter(FileTableModel fileModel, BooleanSupplier sortWithDirectories,
                                  BooleanSupplier naturalNameSort) {
        super(fileModel);
        mySortWithDirectories = sortWithDirectories;
        myNaturalNameSort = naturalNameSort;

        setModelWrapper(new DirAwareModelWrapper(fileModel));
        for (int col = 0; col < fileModel.getColumnCount(); col++) {
            setComparator(col, new ParentFirstComparator(col));
        }
    }

    @Override
    public void setSortKeys(@Nullable List<? extends SortKey> keys) {
        if (keys == null || keys.isEmpty()) {
            super.setSortKeys(keys);
            return;
        }

        SortKey primary = keys.get(0);
        if (primary.getColumn() == FileTableModel.COL_NAME) {
            super.setSortKeys(List.of(primary));
            return;
        }

        // Name ascending as the secondary key, so equal primaries keep a stable order.
        List<SortKey> combined = new ArrayList<>(2);
        combined.add(primary);
        combined.add(new SortKey(FileTableModel.COL_NAME, SortOrder.ASCENDING));
        super.setSortKeys(combined);
    }

    private boolean isDescending(int column) {
        for (SortKey key : getSortKeys()) {
            if (key.getColumn() == column) {
                return key.getSortOrder() == SortOrder.DESCENDING;
            }
        }
        return false;
    }

    private final class DirAwareModelWrapper extends ModelWrapper<FileTableModel, Integer> {
        private final FileTableModel myTableModel;

        DirAwareModelWrapper(FileTableModel tableModel) {
            myTableModel = tableModel;
        }

        @Override
        public FileTableModel getModel() {
            return myTableModel;
        }

        @Override
        public int getColumnCount() {
            return myTableModel.getColumnCount();
        }

        @Override
        public int getRowCount() {
            return myTableModel.getRowCount();
        }

        @Override
        public Integer getIdentifier(int row) {
            return row;
        }

        @Override
        public Object getValueAt(int row, int column) {
            FileEntry entry = myTableModel.getEntryAt(row);
            return new DirAwareValue(myTableModel.getValueAt(row, column),
                entry != null && entry.directory(),
                entry != null && entry.parentLink());
        }

        @Override
        public String getStringValueAt(int row, int column) {
            return String.valueOf(myTableModel.getValueAt(row, column));
        }
    }

    private final class ParentFirstComparator implements Comparator<Object> {
        private final int myColumn;

        ParentFirstComparator(int column) {
            myColumn = column;
        }

        @Override
        public int compare(@Nullable Object o1, @Nullable Object o2) {
            if (!(o1 instanceof DirAwareValue first) || !(o2 instanceof DirAwareValue second)) {
                return 0;
            }

            // ".." always sorts first. TableRowSorter negates the result for DESC, so counter that.
            if (first.parent() || second.parent()) {
                if (first.parent() && second.parent()) {
                    return 0;
                }
                int parentFirst = first.parent() ? -1 : 1;
                return isDescending(myColumn) ? -parentFirst : parentFirst;
            }

            // Group directories before files when the setting is off, with the same DESC
            // counter-reversal so directories stay on top either way.
            if (!mySortWithDirectories.getAsBoolean() && first.directory() != second.directory()) {
                int dirFirst = first.directory() ? -1 : 1;
                return isDescending(myColumn) ? -dirFirst : dirFirst;
            }

            if (isNumericColumn(myColumn)) {
                long l1 = first.value() instanceof Long value ? value : 0L;
                long l2 = second.value() instanceof Long value ? value : 0L;
                return Long.compare(l1, l2);
            }

            String s1 = String.valueOf(first.value());
            String s2 = String.valueOf(second.value());
            if (myColumn == FileTableModel.COL_NAME && myNaturalNameSort.getAsBoolean()) {
                return StringUtil.naturalCompare(s1, s2);
            }
            return s1.compareToIgnoreCase(s2);
        }
    }

    private static boolean isNumericColumn(int column) {
        return column == FileTableModel.COL_SIZE
            || column == FileTableModel.COL_CREATED
            || column == FileTableModel.COL_DATE
            || column == FileTableModel.COL_INODE
            || column == FileTableModel.COL_LINKS;
    }
}
