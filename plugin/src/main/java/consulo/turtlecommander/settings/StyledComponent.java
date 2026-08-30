package consulo.turtlecommander.settings;

import java.util.function.Function;

/**
 * The components that carry their own style, and how each one reaches its {@link ComponentStyle}
 * inside a {@link StyleSet}. Having the mapping in one place keeps the settings page, the theme
 * round-trip and the "reset to defaults" pass from drifting apart as components are added.
 */
public enum StyledComponent {
    TAB("Tab bar", set -> set.tabStyle, ThemeSlot.TAB),
    DRIVE_SELECTOR("Drive selector", set -> set.driveSelectorStyle, ThemeSlot.DRIVE_SELECTOR),
    PATH_BAR("Path bar", set -> set.pathBarStyle, ThemeSlot.PATH_BAR),
    COLUMN_HEADER("Column headers", set -> set.columnHeaderStyle, ThemeSlot.COLUMN_HEADER),
    PANEL("File panel", set -> set.panelStyle, ThemeSlot.PANEL),
    STATUS_BAR("Status bar", set -> set.statusBarStyle, ThemeSlot.STATUS_BAR),
    COMMAND_BAR("Command bar", set -> set.commandBarStyle, ThemeSlot.COMMAND_BAR),
    COMMAND_BUTTON("Cmd buttons", set -> set.commandButtonStyle, ThemeSlot.COMMAND_BUTTON);

    /** Which field of a {@link Theme} a component maps to. */
    private enum ThemeSlot {
        PANEL, TAB, PATH_BAR, STATUS_BAR, COMMAND_BAR, COMMAND_BUTTON, DRIVE_SELECTOR, COLUMN_HEADER
    }

    private final String myLabel;
    private final Function<StyleSet, ComponentStyle> myAccessor;
    private final ThemeSlot mySlot;

    StyledComponent(String label, Function<StyleSet, ComponentStyle> accessor, ThemeSlot slot) {
        myLabel = label;
        myAccessor = accessor;
        mySlot = slot;
    }

    public String getLabel() {
        return myLabel;
    }

    public ComponentStyle styleIn(StyleSet styles) {
        return myAccessor.apply(styles);
    }

    public ThemeStyle styleIn(Theme theme) {
        return switch (mySlot) {
            case PANEL -> theme.panelStyle();
            case TAB -> theme.tabStyle();
            case PATH_BAR -> theme.pathBarStyle();
            case STATUS_BAR -> theme.statusBarStyle();
            case COMMAND_BAR -> theme.commandBarStyle();
            case COMMAND_BUTTON -> theme.commandButtonStyle();
            case DRIVE_SELECTOR -> theme.driveSelectorStyle();
            case COLUMN_HEADER -> theme.columnHeaderStyle();
        };
    }

    /** Builds a theme from one style per component, in the order the record declares them. */
    public static Theme toTheme(String name, Function<StyledComponent, ComponentStyle> styleOf) {
        return new Theme(name,
            ThemeStyle.fromComponentStyle(styleOf.apply(PANEL)),
            ThemeStyle.fromComponentStyle(styleOf.apply(TAB)),
            ThemeStyle.fromComponentStyle(styleOf.apply(PATH_BAR)),
            ThemeStyle.fromComponentStyle(styleOf.apply(STATUS_BAR)),
            ThemeStyle.fromComponentStyle(styleOf.apply(COMMAND_BAR)),
            ThemeStyle.fromComponentStyle(styleOf.apply(COMMAND_BUTTON)),
            ThemeStyle.fromComponentStyle(styleOf.apply(DRIVE_SELECTOR)),
            ThemeStyle.fromComponentStyle(styleOf.apply(COLUMN_HEADER)));
    }
}
