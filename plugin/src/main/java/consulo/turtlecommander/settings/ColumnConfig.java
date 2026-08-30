package consulo.turtlecommander.settings;

import consulo.application.util.SystemInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ColumnConfig {
    public static final List<String> ALL_COLUMN_IDS = List.of(
        "Name", "Ext", "Size", "Date Created", "Date Modified", "User", "Group", "Permissions", "Inode", "Links"
    );

    private static final Set<String> UNIX_ONLY_COLUMNS = Set.of("Group", "Inode", "Links");

    /** Columns that exist but start hidden; users opt in via Settings. */
    private static final Set<String> HIDDEN_BY_DEFAULT_COLUMNS = Set.of("Inode", "Links");

    public String id = "";
    public boolean visible = true;
    public ComponentStyle style = new ComponentStyle();

    /** Whether {@code id} has a usable data source on the current platform. */
    public static boolean isAvailable(String id) {
        return !(SystemInfo.isWindows && UNIX_ONLY_COLUMNS.contains(id));
    }

    /** Column ids usable on the current platform, in canonical order. */
    public static List<String> availableColumnIds() {
        List<String> ids = new ArrayList<>();
        for (String id : ALL_COLUMN_IDS) {
            if (isAvailable(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Whether {@code id} should be visible out of the box on the current platform. */
    public static boolean defaultVisible(String id) {
        return isAvailable(id) && !HIDDEN_BY_DEFAULT_COLUMNS.contains(id);
    }

    public static ColumnConfig of(String id, boolean visible) {
        ColumnConfig config = new ColumnConfig();
        config.id = id;
        config.visible = visible;
        return config;
    }

    public static List<ColumnConfig> defaults() {
        List<ColumnConfig> configs = new ArrayList<>();
        for (String id : availableColumnIds()) {
            configs.add(of(id, defaultVisible(id)));
        }
        return configs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ColumnConfig other)) {
            return false;
        }
        return visible == other.visible && id.equals(other.id) && style.equals(other.style);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, visible, style);
    }
}
