package consulo.turtlecommander.action;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.settings.ColumnConfig;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.util.DateTimeFormatters;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Rendering entries as CSV or JSON for the "Copy as" menu. */
public final class EntryExport {
    private EntryExport() {
    }

    /**
     * The columns an export carries: "Filename" and "Path" are always prepended so the rows stay
     * self-describing even when the user has narrowed the visible Name/Ext columns.
     */
    public static List<String> exportColumnIds() {
        List<String> ids = new ArrayList<>();
        ids.add("Filename");
        ids.add("Path");
        for (ColumnConfig column : effectiveColumns()) {
            if (column.visible) {
                ids.add(column.id);
            }
        }
        return ids;
    }

    private static List<ColumnConfig> effectiveColumns() {
        try {
            return TurtleCommanderSettings.getInstance().getEffectiveColumns();
        }
        catch (Exception e) {
            // No settings service — headless tests. The defaults still produce a usable export.
            return ColumnConfig.defaults();
        }
    }

    /**
     * The raw value of one column: a {@link Long} for numeric columns, a string otherwise, and null
     * when the entry doesn't carry it — a directory's size, an unrecorded date. Name and Ext are
     * split the same way the panel splits them, so an export matches what the user sees.
     */
    public static @Nullable Object columnValue(FileEntry entry, String columnId) {
        return switch (columnId) {
            case "Filename" -> entry.name();
            case "Path" -> entry.path().toString();
            case "Name" -> baseName(entry);
            case "Ext" -> extension(entry);
            case "Size" -> entry.directory() ? null : entry.size();
            case "Date Created" -> formatFileTime(entry.creationTime());
            case "Date Modified" -> formatFileTime(entry.lastModified());
            case "User" -> entry.owner();
            case "Group" -> entry.group();
            case "Permissions" -> entry.permissions();
            case "Inode" -> entry.inode();
            case "Links" -> entry.nlink() != null ? Long.valueOf(entry.nlink()) : null;
            default -> "";
        };
    }

    /** A leading-dot name like {@code .gitignore} is all base name, with no extension. */
    private static String baseName(FileEntry entry) {
        if (entry.directory() || !entry.name().contains(".") || entry.name().startsWith(".")) {
            return entry.name();
        }
        return StringUtil.substringBeforeLast(entry.name(), ".");
    }

    private static String extension(FileEntry entry) {
        if (entry.directory() || !entry.name().contains(".") || entry.name().startsWith(".")) {
            return "";
        }
        return StringUtil.substringAfterLast(entry.name(), ".");
    }

    /**
     * Renders a timestamp for the date columns. A blank format setting keeps the ISO-8601 instant
     * in UTC; a configured pattern is rendered in the system zone, matching the file table. An
     * invalid pattern falls back to the ISO instant rather than producing an empty cell.
     */
    private static String formatFileTime(@Nullable FileTime time) {
        if (time == null) {
            return "";
        }
        Instant instant = Instant.ofEpochMilli(time.toMillis());
        String pattern = exportDateTimeFormat();
        if (!pattern.isBlank()) {
            try {
                return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault())
                    .format(instant);
            }
            catch (Exception e) {
                // Fall through to the ISO default.
            }
        }
        return DateTimeFormatter.ISO_INSTANT.format(instant.atOffset(ZoneOffset.UTC));
    }

    private static String exportDateTimeFormat() {
        try {
            return TurtleCommanderSettings.getInstance().getExportDateTimeFormat();
        }
        catch (Exception e) {
            return "";
        }
    }

    /** Renders a timestamp the way the file table shows it, so a copy matches the display. */
    public static @Nullable String displayFileTime(@Nullable FileTime time) {
        return time != null ? DateTimeFormatters.format(time.toMillis()) : null;
    }

    public static @Nullable String userGroupOf(FileEntry entry) {
        return entry.owner().isBlank() || entry.group().isBlank()
            ? null
            : entry.owner() + ":" + entry.group();
    }

    public static String toCsv(List<FileEntry> entries, List<String> columns) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csvField(columns.get(i)));
        }
        sb.append('\n');

        for (FileEntry entry : entries) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                Object value = columnValue(entry, columns.get(i));
                if (value instanceof Long number) {
                    sb.append(number);
                }
                else if (value != null) {
                    sb.append(csvField(value.toString()));
                }
                // A null value leaves the cell empty.
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public static String toJson(List<FileEntry> entries, List<String> columns) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int index = 0; index < entries.size(); index++) {
            sb.append("  {\n");
            for (int i = 0; i < columns.size(); i++) {
                sb.append("    ").append(jsonString(jsonKeyFor(columns.get(i)))).append(": ");
                Object value = columnValue(entries.get(index), columns.get(i));
                if (value == null) {
                    sb.append("null");
                }
                else if (value instanceof Long number) {
                    sb.append(number);
                }
                else {
                    sb.append(jsonString(value.toString()));
                }
                if (i < columns.size() - 1) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            sb.append("  }");
            if (index < entries.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        return sb.append("]\n").toString();
    }

    private static String jsonKeyFor(String columnId) {
        return StringUtil.toLowerCase(columnId).replace(' ', '_');
    }

    /** Quotes only when needed, so a plain export stays readable. */
    private static String csvField(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
            && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String jsonString(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    }
                    else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
