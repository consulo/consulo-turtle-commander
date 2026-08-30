package consulo.turtlecommander.settings;

import java.util.List;

public record Theme(
    String name,
    ThemeStyle panelStyle,
    ThemeStyle tabStyle,
    ThemeStyle pathBarStyle,
    ThemeStyle statusBarStyle,
    ThemeStyle commandBarStyle,
    ThemeStyle commandButtonStyle,
    ThemeStyle driveSelectorStyle,
    ThemeStyle columnHeaderStyle
) {
    private static final String CONSOLAS = "Consolas";
    private static final String COURIER = "Courier New";

    public static final Theme DEFAULT = new Theme("Default", ThemeStyle.EMPTY, ThemeStyle.EMPTY, ThemeStyle.EMPTY,
        ThemeStyle.EMPTY, ThemeStyle.EMPTY, ThemeStyle.EMPTY, ThemeStyle.EMPTY, ThemeStyle.EMPTY);

    public static final Theme CLASSIC_NC = new Theme(
        "Classic NC",
        ThemeStyle.ofPanel(CONSOLAS, 13, "#00FFFF", "#000080", "#000050", "#0000AA"),
        ThemeStyle.of(CONSOLAS, 12),
        ThemeStyle.of(CONSOLAS, 13, "#FFFFFF", "#000080"),
        ThemeStyle.of(CONSOLAS, 13, "#00FFFF", "#000080"),
        ThemeStyle.ofBackground(CONSOLAS, 13, "#000000"),
        ThemeStyle.of(CONSOLAS, 13, "#000000", "#00AAAA"),
        ThemeStyle.of(CONSOLAS, 13, "#FFFFFF", "#000080"),
        ThemeStyle.of(CONSOLAS, 13, "#FFFF00", "#000080")
    );

    public static final Theme GREEN_TERMINAL = new Theme(
        "Green Terminal",
        ThemeStyle.ofPanel(CONSOLAS, 13, "#33FF33", "#0A0A0A", "#1A3A1A", "#0A4A0A"),
        ThemeStyle.of(CONSOLAS, 12),
        ThemeStyle.of(CONSOLAS, 13, "#33FF33", "#0A0A0A"),
        ThemeStyle.of(CONSOLAS, 13, "#33FF33", "#0A0A0A"),
        ThemeStyle.ofBackground(CONSOLAS, 13, "#0A0A0A"),
        ThemeStyle.of(CONSOLAS, 13, "#33FF33", "#1A1A1A"),
        ThemeStyle.of(CONSOLAS, 13, "#33FF33", "#0A0A0A"),
        ThemeStyle.of(CONSOLAS, 13, "#00CC00", "#1A1A1A")
    );

    public static final Theme BROWN_OLDSCHOOL = new Theme(
        "Brown Oldschool",
        ThemeStyle.ofPanel(COURIER, 13, "#FFD700", "#3B2507", "#5C3A11", "#7A4E1A"),
        ThemeStyle.of(COURIER, 12),
        ThemeStyle.of(COURIER, 13, "#FFDEAD", "#3B2507"),
        ThemeStyle.of(COURIER, 13, "#FFDEAD", "#3B2507"),
        ThemeStyle.ofBackground(COURIER, 13, "#3B2507"),
        ThemeStyle.of(COURIER, 13, "#FFD700", "#5C3A11"),
        ThemeStyle.of(COURIER, 13, "#FFD700", "#3B2507"),
        ThemeStyle.of(COURIER, 13, "#FFA500", "#5C3A11")
    );

    public static final Theme GREY_ASH = new Theme(
        "Grey Ash",
        ThemeStyle.ofPanel(CONSOLAS, 13, "#CCCCBB", "#3D3D35", "#4D4D45", "#5D5D55"),
        ThemeStyle.of(CONSOLAS, 12),
        ThemeStyle.of(CONSOLAS, 13, "#DDDDCC", "#3D3D35"),
        ThemeStyle.of(CONSOLAS, 13, "#BBBBAA", "#3D3D35"),
        ThemeStyle.ofBackground(CONSOLAS, 13, "#3D3D35"),
        ThemeStyle.of(CONSOLAS, 13, "#CCCCBB", "#4D4D45"),
        ThemeStyle.of(CONSOLAS, 13, "#CCCCBB", "#3D3D35"),
        ThemeStyle.of(CONSOLAS, 13, "#AAAA99", "#4D4D45")
    );

    public static final Theme MONOCHROME = new Theme(
        "Monochrome",
        ThemeStyle.ofPanel(CONSOLAS, 13, "#FFFFFF", "#000000", "#222222", "#444444"),
        ThemeStyle.of(CONSOLAS, 12),
        ThemeStyle.of(CONSOLAS, 13, "#FFFFFF", "#000000"),
        ThemeStyle.of(CONSOLAS, 13, "#CCCCCC", "#000000"),
        ThemeStyle.ofBackground(CONSOLAS, 13, "#000000"),
        ThemeStyle.of(CONSOLAS, 13, "#FFFFFF", "#000000"),
        ThemeStyle.of(CONSOLAS, 13, "#FFFFFF", "#000000"),
        ThemeStyle.of(CONSOLAS, 13, "#999999", "#000000")
    );

    /** Themes seeded on first plugin install. */
    public static final List<Theme> INITIAL_THEMES =
        List.of(DEFAULT, CLASSIC_NC, GREEN_TERMINAL, BROWN_OLDSCHOOL, GREY_ASH, MONOCHROME);

    public static Theme named(String name) {
        return new Theme(name, ThemeStyle.EMPTY, ThemeStyle.EMPTY, ThemeStyle.EMPTY, ThemeStyle.EMPTY,
            ThemeStyle.EMPTY, ThemeStyle.EMPTY, ThemeStyle.EMPTY, ThemeStyle.EMPTY);
    }

    public void applyTo(TurtleCommanderSettings.State state) {
        state.styles.panelStyle = panelStyle.toComponentStyle();
        state.styles.tabStyle = tabStyle.toComponentStyle();
        state.styles.pathBarStyle = pathBarStyle.toComponentStyle();
        state.styles.statusBarStyle = statusBarStyle.toComponentStyle();
        state.styles.commandBarStyle = commandBarStyle.toComponentStyle();
        state.styles.commandButtonStyle = commandButtonStyle.toComponentStyle();
        state.styles.driveSelectorStyle = driveSelectorStyle.toComponentStyle();
        state.styles.columnHeaderStyle = columnHeaderStyle.toComponentStyle();
    }

    @Override
    public String toString() {
        return name;
    }
}
