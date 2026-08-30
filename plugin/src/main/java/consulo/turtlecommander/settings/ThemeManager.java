package consulo.turtlecommander.settings;

import org.jspecify.annotations.Nullable;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages the theme list persisted in settings. On first use the built-in themes are seeded;
 * after that every theme is user-owned and freely editable.
 */
public final class ThemeManager {
    public enum SaveResult {
        SAVED, REPLACED
    }

    public enum RenameResult {
        SUCCESS, CONFLICT, NOT_FOUND, INVALID_NAME
    }

    public enum ImportResult {
        IMPORTED, REPLACED, RENAMED
    }

    /** Outcome of an import, together with the name the theme ended up with. */
    public record ImportOutcome(ImportResult result, String name) {
    }

    private ThemeManager() {
    }

    public static void ensureInitialThemes(TurtleCommanderSettings.State state) {
        if (state.themesInitialized) {
            return;
        }
        for (Theme theme : Theme.INITIAL_THEMES) {
            state.themes.add(SavedTheme.fromTheme(theme));
        }
        state.themesInitialized = true;
    }

    /** All themes with "Default" pinned first and the rest sorted by name. */
    public static List<Theme> getAllThemes(TurtleCommanderSettings.State state) {
        ensureInitialThemes(state);

        List<Theme> defaults = new ArrayList<>();
        List<Theme> rest = new ArrayList<>();
        for (SavedTheme saved : state.themes) {
            Theme theme = saved.toTheme();
            if (theme.name().equals(Theme.DEFAULT.name())) {
                defaults.add(theme);
            }
            else {
                rest.add(theme);
            }
        }
        rest.sort(Comparator.comparing(Theme::name));

        List<Theme> all = new ArrayList<>(defaults);
        all.addAll(rest);
        return all;
    }

    public static SaveResult saveTheme(TurtleCommanderSettings.State state, Theme theme) {
        int existing = indexOfTheme(state, theme.name());
        if (existing >= 0) {
            state.themes.set(existing, SavedTheme.fromTheme(theme));
            return SaveResult.REPLACED;
        }
        state.themes.add(SavedTheme.fromTheme(theme));
        return SaveResult.SAVED;
    }

    public static boolean deleteTheme(TurtleCommanderSettings.State state, String themeName) {
        return state.themes.removeIf(saved -> saved.name.equals(themeName));
    }

    public static RenameResult renameTheme(TurtleCommanderSettings.State state, String oldName, String newName) {
        if (newName.isBlank()) {
            return RenameResult.INVALID_NAME;
        }
        if (oldName.equals(newName)) {
            return RenameResult.SUCCESS;
        }
        if (indexOfTheme(state, newName) >= 0) {
            return RenameResult.CONFLICT;
        }
        int index = indexOfTheme(state, oldName);
        if (index < 0) {
            return RenameResult.NOT_FOUND;
        }
        state.themes.get(index).name = newName;
        return RenameResult.SUCCESS;
    }

    public static String exportTheme(Theme theme) {
        StringBuilder sb = new StringBuilder();
        sb.append("name=").append(theme.name()).append('\n');
        appendStyle(sb, "panel", theme.panelStyle());
        appendStyle(sb, "tab", theme.tabStyle());
        appendStyle(sb, "pathBar", theme.pathBarStyle());
        appendStyle(sb, "statusBar", theme.statusBarStyle());
        appendStyle(sb, "commandBar", theme.commandBarStyle());
        appendStyle(sb, "commandButton", theme.commandButtonStyle());
        appendStyle(sb, "driveSelector", theme.driveSelectorStyle());
        appendStyle(sb, "columnHeader", theme.columnHeaderStyle());
        return sb.toString();
    }

    public static @Nullable Theme importTheme(String text) {
        Map<String, String> props = new HashMap<>();
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                continue;
            }
            props.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }

        String name = props.get("name");
        if (name == null || name.isBlank()) {
            return null;
        }
        return new Theme(
            name,
            parseStyle(props, "panel"),
            parseStyle(props, "tab"),
            parseStyle(props, "pathBar"),
            parseStyle(props, "statusBar"),
            parseStyle(props, "commandBar"),
            parseStyle(props, "commandButton"),
            parseStyle(props, "driveSelector"),
            parseStyle(props, "columnHeader")
        );
    }

    /**
     * Imports a theme into state. When the name already exists, {@code overwrite} decides between
     * replacing the existing entry and importing under a generated unique name.
     */
    public static ImportOutcome importThemeToState(TurtleCommanderSettings.State state, Theme theme, boolean overwrite) {
        int existing = indexOfTheme(state, theme.name());
        if (existing >= 0) {
            if (overwrite) {
                state.themes.set(existing, SavedTheme.fromTheme(theme));
                return new ImportOutcome(ImportResult.REPLACED, theme.name());
            }
            String newName = generateUniqueName(theme.name(), state);
            SavedTheme saved = SavedTheme.fromTheme(theme);
            saved.name = newName;
            state.themes.add(saved);
            return new ImportOutcome(ImportResult.RENAMED, newName);
        }
        state.themes.add(SavedTheme.fromTheme(theme));
        return new ImportOutcome(ImportResult.IMPORTED, theme.name());
    }

    static String generateUniqueName(String baseName, TurtleCommanderSettings.State state) {
        Set<String> allNames = new HashSet<>();
        for (SavedTheme saved : state.themes) {
            allNames.add(saved.name);
        }
        String candidate = baseName + " (2)";
        int counter = 3;
        while (allNames.contains(candidate)) {
            candidate = baseName + " (" + counter + ")";
            counter++;
        }
        return candidate;
    }

    private static int indexOfTheme(TurtleCommanderSettings.State state, String name) {
        for (int i = 0; i < state.themes.size(); i++) {
            if (state.themes.get(i).name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static void appendStyle(StringBuilder sb, String prefix, ThemeStyle style) {
        appendIfSet(sb, prefix, "fontFamily", style.fontFamily());
        if (style.fontSize() > 0) {
            sb.append(prefix).append(".fontSize=").append(style.fontSize()).append('\n');
        }
        if (style.fontStyle() != Font.PLAIN) {
            sb.append(prefix).append(".fontStyle=").append(style.fontStyle()).append('\n');
        }
        appendIfSet(sb, prefix, "fontColor", style.fontColor());
        appendIfSet(sb, prefix, "backgroundColor", style.backgroundColor());
        appendIfSet(sb, prefix, "selectedColor", style.selectedColor());
        appendIfSet(sb, prefix, "activeSelectedColor", style.activeSelectedColor());
    }

    private static void appendIfSet(StringBuilder sb, String prefix, String key, String value) {
        if (!value.isEmpty()) {
            sb.append(prefix).append('.').append(key).append('=').append(value).append('\n');
        }
    }

    private static ThemeStyle parseStyle(Map<String, String> props, String prefix) {
        return new ThemeStyle(
            props.getOrDefault(prefix + ".fontFamily", ""),
            parseInt(props.get(prefix + ".fontSize"), 0),
            parseInt(props.get(prefix + ".fontStyle"), Font.PLAIN),
            props.getOrDefault(prefix + ".fontColor", ""),
            props.getOrDefault(prefix + ".backgroundColor", ""),
            props.getOrDefault(prefix + ".selectedColor", ""),
            props.getOrDefault(prefix + ".activeSelectedColor", "")
        );
    }

    private static int parseInt(@Nullable String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e) {
            return fallback;
        }
    }
}
