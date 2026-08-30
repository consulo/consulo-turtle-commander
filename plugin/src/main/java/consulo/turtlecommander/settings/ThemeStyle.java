package consulo.turtlecommander.settings;

import java.awt.Font;

/**
 * Immutable counterpart of {@link ComponentStyle}, used to declare the built-in themes and to
 * round-trip themes through {@link SavedTheme}.
 */
public record ThemeStyle(
    String fontFamily,
    int fontSize,
    int fontStyle,
    String fontColor,
    String backgroundColor,
    String selectedColor,
    String activeSelectedColor
) {
    public static final ThemeStyle EMPTY = new ThemeStyle("", 0, Font.PLAIN, "", "", "", "");

    /** Font family + size only; used by the tab style, which has no colours. */
    public static ThemeStyle of(String fontFamily, int fontSize) {
        return new ThemeStyle(fontFamily, fontSize, Font.PLAIN, "", "", "", "");
    }

    /** Family, size, foreground and background; the shape most built-in styles use. */
    public static ThemeStyle of(String fontFamily, int fontSize, String fontColor, String backgroundColor) {
        return new ThemeStyle(fontFamily, fontSize, Font.PLAIN, fontColor, backgroundColor, "", "");
    }

    /** Background only; used by the command bar, which inherits its foreground. */
    public static ThemeStyle ofBackground(String fontFamily, int fontSize, String backgroundColor) {
        return new ThemeStyle(fontFamily, fontSize, Font.PLAIN, "", backgroundColor, "", "");
    }

    /** The full panel style, including both selection colours. */
    public static ThemeStyle ofPanel(String fontFamily, int fontSize, String fontColor, String backgroundColor,
                                     String selectedColor, String activeSelectedColor) {
        return new ThemeStyle(fontFamily, fontSize, Font.PLAIN, fontColor, backgroundColor, selectedColor, activeSelectedColor);
    }

    public ComponentStyle toComponentStyle() {
        ComponentStyle style = new ComponentStyle();
        style.fontFamily = fontFamily;
        style.fontSize = fontSize;
        style.fontStyle = fontStyle;
        style.fontColor = fontColor;
        style.backgroundColor = backgroundColor;
        style.selectedColor = selectedColor;
        style.activeSelectedColor = activeSelectedColor;
        return style;
    }

    public static ThemeStyle fromComponentStyle(ComponentStyle style) {
        return new ThemeStyle(style.fontFamily, style.fontSize, style.fontStyle, style.fontColor,
            style.backgroundColor, style.selectedColor, style.activeSelectedColor);
    }
}
