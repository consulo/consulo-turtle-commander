package consulo.turtlecommander.ui;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.util.DateTimeFormatters;
import consulo.turtlecommander.util.FileUtils;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.function.Function;

/**
 * Backing model for the table view.
 *
 * <p>{@link #getValueAt} returns sortable values (numbers for size/date/inode/links) while
 * {@link #getDisplayValue} returns what the renderer paints. The ".." row uses sentinel values so
 * it sorts to the top in either direction — see {@link ParentPinningRowSorter}, which also pins it.
 */
public class FileTableModel extends AbstractTableModel {
    public static final int COL_NAME = 0;
    public static final int COL_EXT = 1;
    public static final int COL_SIZE = 2;
    public static final int COL_CREATED = 3;
    public static final int COL_DATE = 4;
    public static final int COL_OWNER = 5;
    public static final int COL_GROUP = 6;
    public static final int COL_PERMS = 7;
    public static final int COL_INODE = 8;
    public static final int COL_LINKS = 9;

    public static final String PARENT_MARKER = "..";
    public static final long PARENT_NUMERIC = Long.MIN_VALUE;
    public static final long DIR_NUMERIC = -1L;

    private static final String[] COLUMNS = {
        "Name", "Ext", "Size", "Date Created", "Date Modified", "User", "Group", "Permissions", "Inode", "Links"
    };

    private List<FileEntry> myEntries = List.of();
    private @Nullable Function<Path, @Nullable Long> myDirectorySizeProvider;

    public void setEntries(List<FileEntry> entries) {
        myEntries = List.copyOf(entries);
        fireTableDataChanged();
    }

    public void setDirectorySizeProvider(@Nullable Function<Path, @Nullable Long> provider) {
        myDirectorySizeProvider = provider;
    }

    public @Nullable FileEntry getEntryAt(int row) {
        return row >= 0 && row < myEntries.size() ? myEntries.get(row) : null;
    }

    @Override
    public int getRowCount() {
        return myEntries.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case COL_SIZE, COL_CREATED, COL_DATE, COL_INODE, COL_LINKS -> Long.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        FileEntry entry = getEntryAt(rowIndex);
        if (entry == null) {
            return "";
        }

        return switch (columnIndex) {
            case COL_NAME -> baseName(entry);
            case COL_EXT -> extension(entry);
            case COL_SIZE -> {
                if (entry.parentLink()) {
                    yield PARENT_NUMERIC;
                }
                if (!entry.directory()) {
                    yield entry.size();
                }
                Long calculated = directorySize(entry.path());
                yield calculated != null ? calculated : DIR_NUMERIC;
            }
            case COL_CREATED -> entry.parentLink() ? PARENT_NUMERIC : millisOrZero(entry.creationTime());
            case COL_DATE -> entry.parentLink() ? PARENT_NUMERIC : millisOrZero(entry.lastModified());
            case COL_OWNER -> entry.parentLink() ? PARENT_MARKER : entry.owner();
            case COL_GROUP -> entry.parentLink() ? PARENT_MARKER : entry.group();
            case COL_PERMS -> entry.parentLink() ? PARENT_MARKER : entry.permissions();
            case COL_INODE -> entry.parentLink() ? PARENT_NUMERIC : (entry.inode() != null ? entry.inode() : 0L);
            case COL_LINKS -> entry.parentLink() ? PARENT_NUMERIC
                : (entry.nlink() != null ? entry.nlink().longValue() : 0L);
            default -> "";
        };
    }

    /** What the renderer paints, as opposed to what the sorter compares. */
    public String getDisplayValue(int rowIndex, int columnIndex) {
        FileEntry entry = getEntryAt(rowIndex);
        if (entry == null) {
            return "";
        }
        if (entry.parentLink()) {
            return columnIndex == COL_NAME ? PARENT_MARKER : "";
        }

        return switch (columnIndex) {
            case COL_SIZE -> {
                if (!entry.directory()) {
                    yield FileUtils.formatSize(entry.size());
                }
                Long calculated = directorySize(entry.path());
                yield calculated != null ? FileUtils.formatSize(calculated) : "<DIR>";
            }
            case COL_CREATED -> formatTime(entry.creationTime());
            case COL_DATE -> formatTime(entry.lastModified());
            case COL_INODE -> entry.inode() != null ? entry.inode().toString() : "";
            case COL_LINKS -> entry.nlink() != null ? entry.nlink().toString() : "";
            default -> String.valueOf(getValueAt(rowIndex, columnIndex));
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        if (columnIndex != COL_NAME) {
            return false;
        }
        FileEntry entry = getEntryAt(rowIndex);
        return entry != null && !entry.parentLink();
    }

    @Override
    public void setValueAt(@Nullable Object value, int rowIndex, int columnIndex) {
        // Rename is driven by the cell editor's stop callback in FileTab, not through the model.
    }

    /** Everything before the last dot — except for dotfiles, which are all "name". */
    private static String baseName(FileEntry entry) {
        if (entry.parentLink()) {
            return PARENT_MARKER;
        }
        String name = entry.name();
        if (entry.directory() || !hasExtension(name)) {
            return name;
        }
        return StringUtil.substringBeforeLast(name, ".");
    }

    private static String extension(FileEntry entry) {
        if (entry.parentLink()) {
            return PARENT_MARKER;
        }
        String name = entry.name();
        if (entry.directory() || !hasExtension(name)) {
            return "";
        }
        return StringUtil.notNullize(StringUtil.substringAfterLast(name, "."));
    }

    private static boolean hasExtension(String name) {
        return name.indexOf('.') >= 0 && !name.startsWith(".");
    }

    private @Nullable Long directorySize(Path path) {
        Function<Path, @Nullable Long> provider = myDirectorySizeProvider;
        return provider != null ? provider.apply(path) : null;
    }

    private static long millisOrZero(@Nullable FileTime time) {
        return time != null ? time.toMillis() : 0L;
    }

    private static String formatTime(@Nullable FileTime time) {
        return time != null ? DateTimeFormatters.format(time.toMillis()) : "";
    }
}
