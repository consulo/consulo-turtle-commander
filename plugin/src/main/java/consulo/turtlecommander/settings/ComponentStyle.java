package consulo.turtlecommander.settings;

import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.awt.Font;
import java.util.Objects;

/**
 * Per-component font/colour overrides. Every field is a plain bean property because the state
 * is serialised by the XML serializer; empty string and 0 mean "inherit the platform default".
 */
public class ComponentStyle {
    private static final int FALLBACK_FONT_SIZE = 13;

    public String fontFamily = "";
    public int fontSize = 0;
    public int fontStyle = Font.PLAIN;
    public String fontColor = "";
    public String backgroundColor = "";
    public String selectedColor = "";
    public String activeSelectedColor = "";

    public @Nullable Font getFont(@Nullable Font defaultFont) {
        if (fontFamily.isEmpty() && fontSize <= 0 && fontStyle == Font.PLAIN) {
            return null;
        }
        int baseSize = fontSize > 0 ? fontSize : (defaultFont != null ? defaultFont.getSize() : FALLBACK_FONT_SIZE);
        String family = !fontFamily.isEmpty() ? fontFamily : (defaultFont != null ? defaultFont.getFamily() : Font.DIALOG);
        return new Font(family, fontStyle, baseSize);
    }

    public @Nullable Color parsedFontColor() {
        return parseColor(fontColor);
    }

    public @Nullable Color parsedBackgroundColor() {
        return parseColor(backgroundColor);
    }

    public @Nullable Color parsedSelectedColor() {
        return parseColor(selectedColor);
    }

    public @Nullable Color parsedActiveSelectedColor() {
        return parseColor(activeSelectedColor);
    }

    public void copyFrom(ComponentStyle other) {
        fontFamily = other.fontFamily;
        fontSize = other.fontSize;
        fontStyle = other.fontStyle;
        fontColor = other.fontColor;
        backgroundColor = other.backgroundColor;
        selectedColor = other.selectedColor;
        activeSelectedColor = other.activeSelectedColor;
    }

    public boolean isDefault() {
        return fontFamily.isEmpty() && fontSize == 0 && fontStyle == Font.PLAIN
            && fontColor.isEmpty() && backgroundColor.isEmpty()
            && selectedColor.isEmpty() && activeSelectedColor.isEmpty();
    }

    static @Nullable Color parseColor(String hex) {
        if (hex.isBlank()) {
            return null;
        }
        try {
            return Color.decode(hex);
        }
        catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ComponentStyle other)) {
            return false;
        }
        return fontSize == other.fontSize
            && fontStyle == other.fontStyle
            && fontFamily.equals(other.fontFamily)
            && fontColor.equals(other.fontColor)
            && backgroundColor.equals(other.backgroundColor)
            && selectedColor.equals(other.selectedColor)
            && activeSelectedColor.equals(other.activeSelectedColor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fontFamily, fontSize, fontStyle, fontColor, backgroundColor, selectedColor, activeSelectedColor);
    }
}
