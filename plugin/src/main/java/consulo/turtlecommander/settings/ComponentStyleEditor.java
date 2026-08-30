package consulo.turtlecommander.settings;

import consulo.ui.ex.awt.ComboBox;
import org.jspecify.annotations.Nullable;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

/**
 * The font and colour controls for one styled component — the panel, the tab bar, the status bar
 * and so on. Also holds the "legacy" family and size, which are the pre-per-component settings a
 * user may still be carrying, so an untouched editor doesn't silently reset them.
 *
 * <p>The family picker is a plain combo over the installed families: the platform's own
 * {@code FontComboBox} lives in an impl module that plugins can't depend on.
 */
public class ComponentStyleEditor {
    /** The size a component falls back to when neither the style nor a legacy setting has one. */
    private static final int DEFAULT_FONT_SIZE = 13;

    private static final String DEFAULT_FAMILY_LABEL = "Default";
    private static final String[] STYLE_LABELS = {"Plain", "Bold", "Italic", "Bold Italic"};

    private final String myLabel;

    private final ComboBox<String> myFontCombo;
    private final JSpinner mySizeSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_FONT_SIZE, 8, 48, 1));
    private final ComboBox<String> myStyleCombo = new ComboBox<>(new DefaultComboBoxModel<>(STYLE_LABELS));

    private final ColorPickerButton myColorButton = new ColorPickerButton();
    private final ColorPickerButton myBackgroundColorButton = new ColorPickerButton();
    private final ColorPickerButton mySelectedColorButton = new ColorPickerButton();
    private final ColorPickerButton myActiveSelectedColorButton = new ColorPickerButton();

    public ComponentStyleEditor(String label, ComponentStyle style) {
        this(label, style, "", 0);
    }

    public ComponentStyleEditor(String label, ComponentStyle style, String legacyFamily, int legacySize) {
        myLabel = label;

        List<String> families = new ArrayList<>();
        families.add(DEFAULT_FAMILY_LABEL);
        families.addAll(List.of(GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getAvailableFontFamilyNames()));
        myFontCombo = new ComboBox<>(new DefaultComboBoxModel<>(families.toArray(new String[0])));
        myFontCombo.setPreferredSize(new Dimension(160, myFontCombo.getPreferredSize().height));
        mySizeSpinner.setPreferredSize(new Dimension(60, mySizeSpinner.getPreferredSize().height));

        resetFrom(style, legacyFamily, legacySize);
    }

    public String getLabel() {
        return myLabel;
    }

    public ComboBox<String> getFontCombo() {
        return myFontCombo;
    }

    public JSpinner getSizeSpinner() {
        return mySizeSpinner;
    }

    public ComboBox<String> getStyleCombo() {
        return myStyleCombo;
    }

    public ColorPickerButton getColorButton() {
        return myColorButton;
    }

    public ColorPickerButton getBackgroundColorButton() {
        return myBackgroundColorButton;
    }

    public ColorPickerButton getSelectedColorButton() {
        return mySelectedColorButton;
    }

    public ColorPickerButton getActiveSelectedColorButton() {
        return myActiveSelectedColorButton;
    }

    public void resetFrom(ComponentStyle style) {
        resetFrom(style, "", 0);
    }

    public void resetFrom(ComponentStyle style, String legacyFamily, int legacySize) {
        myFontCombo.setSelectedItem(familyOf(style, legacyFamily));
        mySizeSpinner.setValue(sizeOf(style, legacySize));
        myStyleCombo.setSelectedIndex(styleIndexOf(style.fontStyle));

        myColorButton.setColor(style.parsedFontColor());
        myBackgroundColorButton.setColor(style.parsedBackgroundColor());
        mySelectedColorButton.setColor(style.parsedSelectedColor());
        myActiveSelectedColorButton.setColor(style.parsedActiveSelectedColor());
    }

    public void applyTo(ComponentStyle style) {
        Object family = myFontCombo.getSelectedItem();
        style.fontFamily = family == null || DEFAULT_FAMILY_LABEL.equals(family) ? "" : family.toString();
        style.fontSize = ((Number) mySizeSpinner.getValue()).intValue();
        style.fontStyle = styleOfIndex(myStyleCombo.getSelectedIndex());
        style.fontColor = myColorButton.getColorHex();
        style.backgroundColor = myBackgroundColorButton.getColorHex();
        style.selectedColor = mySelectedColorButton.getColorHex();
        style.activeSelectedColor = myActiveSelectedColorButton.getColorHex();
    }

    /**
     * Compares against the values the editor was populated with, legacy fallbacks included — so an
     * editor the user never touched never reports itself modified.
     */
    public boolean isModified(ComponentStyle style, String legacyFamily, int legacySize) {
        ComponentStyle current = new ComponentStyle();
        applyTo(current);

        return !current.fontFamily.equals(familyValueOf(style, legacyFamily))
            || current.fontSize != sizeOf(style, legacySize)
            || current.fontStyle != style.fontStyle
            || !current.fontColor.equals(style.fontColor)
            || !current.backgroundColor.equals(style.backgroundColor)
            || !current.selectedColor.equals(style.selectedColor)
            || !current.activeSelectedColor.equals(style.activeSelectedColor);
    }

    public boolean isModified(ComponentStyle style) {
        return isModified(style, "", 0);
    }

    private static String familyValueOf(ComponentStyle style, String legacyFamily) {
        return style.fontFamily.isEmpty() ? legacyFamily : style.fontFamily;
    }

    private static String familyOf(ComponentStyle style, String legacyFamily) {
        String family = familyValueOf(style, legacyFamily);
        return family.isEmpty() ? DEFAULT_FAMILY_LABEL : family;
    }

    private static int sizeOf(ComponentStyle style, int legacySize) {
        if (style.fontSize > 0) {
            return style.fontSize;
        }
        return legacySize > 0 ? legacySize : DEFAULT_FONT_SIZE;
    }

    private static int styleIndexOf(int fontStyle) {
        return switch (fontStyle) {
            case Font.BOLD -> 1;
            case Font.ITALIC -> 2;
            case Font.BOLD | Font.ITALIC -> 3;
            default -> 0;
        };
    }

    private static int styleOfIndex(int index) {
        return switch (index) {
            case 1 -> Font.BOLD;
            case 2 -> Font.ITALIC;
            case 3 -> Font.BOLD | Font.ITALIC;
            default -> Font.PLAIN;
        };
    }
}
