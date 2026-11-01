package consulo.turtlecommander.settings;

import consulo.localize.LocalizeValue;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.FontBox;
import consulo.ui.IntBox;
import consulo.ui.Label;
import consulo.ui.Space;
import consulo.ui.StaticPosition;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.TableLayout;
import org.jspecify.annotations.Nullable;

import java.awt.Font;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The font and colour controls for one styled component — the panel, the tab bar, the status bar
 * and so on. Also holds the "legacy" family and size, which are the pre-per-component settings a
 * user may still be carrying, so an untouched editor doesn't silently reset them.
 *
 * <p>{@link FontBox} has no "no family" entry, so the default family is a check box beside it.
 */
public class ComponentStyleEditor {
    /** Which groups of rows the form shows; a component only gets the rows it can actually render. */
    public enum Section {
        /** Font family, size, style and colour. */
        FONT,
        BACKGROUND,
        /** Selected and active-selected colours - only the file panel has a selection. */
        SELECTION
    }

    /** The size a component falls back to when neither the style nor a legacy setting has one. */
    private static final int DEFAULT_FONT_SIZE = 13;
    private static final int MIN_FONT_SIZE = 8;
    private static final int MAX_FONT_SIZE = 48;

    private static final List<Integer> FONT_STYLES = List.of(Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC);

    private final String myLabel;

    private final FontBox myFontBox;
    private final CheckBox myDefaultFontCheck;
    private final IntBox mySizeBox;
    private final ComboBox<Integer> myStyleBox;

    private final UnifiedColorPicker myColorPicker;
    private final UnifiedColorPicker myBackgroundColorPicker;
    private final UnifiedColorPicker mySelectedColorPicker;
    private final UnifiedColorPicker myActiveSelectedColorPicker;

    private Set<Section> mySections = EnumSet.of(Section.FONT);
    private @Nullable Component myComponent;

    @RequiredUIAccess
    public ComponentStyleEditor(String label, ComponentStyle style) {
        this(label, style, "", 0);
    }

    @RequiredUIAccess
    public ComponentStyleEditor(String label, ComponentStyle style, String legacyFamily, int legacySize) {
        myLabel = label;

        myFontBox = FontBox.create();
        myDefaultFontCheck = CheckBox.create(LocalizeValue.localizeTODO("Default"));
        myDefaultFontCheck.addValueListener(event -> updateFontBoxEnabled());
        mySizeBox = IntBox.create(DEFAULT_FONT_SIZE).withRange(MIN_FONT_SIZE, MAX_FONT_SIZE);
        myStyleBox = ComboBox.create(FONT_STYLES);
        myStyleBox.setTextRenderer(ComponentStyleEditor::styleLabel);

        myColorPicker = new UnifiedColorPicker();
        myBackgroundColorPicker = new UnifiedColorPicker();
        mySelectedColorPicker = new UnifiedColorPicker();
        myActiveSelectedColorPicker = new UnifiedColorPicker();

        resetFrom(style, legacyFamily, legacySize);
    }

    public String getLabel() {
        return myLabel;
    }

    /**
     * Chooses the rows {@link #getComponent()} builds; only {@link Section#FONT} by default. Takes effect only
     * before the form is first built.
     */
    public ComponentStyleEditor withSections(Set<Section> sections) {
        mySections = sections.isEmpty() ? EnumSet.noneOf(Section.class) : EnumSet.copyOf(sections);
        return this;
    }

    /** The label/field form, built once: the controls are single-parent components. */
    @RequiredUIAccess
    public Component getComponent() {
        Component component = myComponent;
        if (component == null) {
            component = buildForm();
            myComponent = component;
        }
        return component;
    }

    @RequiredUIAccess
    private Component buildForm() {
        TableLayout form = TableLayout.create(StaticPosition.TOP);
        int row = 0;
        if (mySections.contains(Section.FONT)) {
            row = addRow(form, row, "Font:", HorizontalLayout.create(Space.SMALL).add(myFontBox).add(myDefaultFontCheck));
            row = addRow(form, row, "Size:", mySizeBox);
            row = addRow(form, row, "Style:", myStyleBox);
            row = addRow(form, row, "Color:", myColorPicker.getComponent());
        }
        if (mySections.contains(Section.BACKGROUND)) {
            row = addRow(form, row, "Background:", myBackgroundColorPicker.getComponent());
        }
        if (mySections.contains(Section.SELECTION)) {
            row = addRow(form, row, "Selected:", mySelectedColorPicker.getComponent());
            addRow(form, row, "Active selected:", myActiveSelectedColorPicker.getComponent());
        }
        return form;
    }

    @RequiredUIAccess
    private static int addRow(TableLayout form, int row, String text, Component field) {
        // the table lines the labels up in their column; a label takes no width of its own on every frontend
        form.add(Label.create(LocalizeValue.localizeTODO(text)), TableLayout.cell(row, 0));
        form.add(field, TableLayout.cell(row, 1));
        return row + 1;
    }

    @RequiredUIAccess
    public void resetFrom(ComponentStyle style) {
        resetFrom(style, "", 0);
    }

    @RequiredUIAccess
    public void resetFrom(ComponentStyle style, String legacyFamily, int legacySize) {
        String family = familyValueOf(style, legacyFamily);
        myDefaultFontCheck.setValue(family.isEmpty(), false);
        if (!family.isEmpty()) {
            myFontBox.setValue(family, false);
        }
        updateFontBoxEnabled();
        mySizeBox.setValue(sizeOf(style, legacySize), false);
        myStyleBox.setValue(FONT_STYLES.contains(style.fontStyle) ? style.fontStyle : Font.PLAIN, false);

        myColorPicker.setColor(style.parsedFontColor());
        myBackgroundColorPicker.setColor(style.parsedBackgroundColor());
        mySelectedColorPicker.setColor(style.parsedSelectedColor());
        myActiveSelectedColorPicker.setColor(style.parsedActiveSelectedColor());
    }

    public void applyTo(ComponentStyle style) {
        String family = myFontBox.getValue();
        style.fontFamily = Boolean.TRUE.equals(myDefaultFontCheck.getValue()) || family == null ? "" : family;
        style.fontSize = currentSize();
        Integer fontStyle = myStyleBox.getValue();
        style.fontStyle = fontStyle != null ? fontStyle : Font.PLAIN;
        style.fontColor = myColorPicker.getColorHex();
        style.backgroundColor = myBackgroundColorPicker.getColorHex();
        style.selectedColor = mySelectedColorPicker.getColorHex();
        style.activeSelectedColor = myActiveSelectedColorPicker.getColorHex();
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

    @RequiredUIAccess
    private void updateFontBoxEnabled() {
        myFontBox.setEnabled(!Boolean.TRUE.equals(myDefaultFontCheck.getValue()));
    }

    /**
     * An emptied box reads as the default size. Not clamped: a stored size outside the range must read back
     * unchanged, or an untouched editor would report itself modified.
     */
    private int currentSize() {
        Integer size = mySizeBox.getValue();
        return size != null ? size : DEFAULT_FONT_SIZE;
    }

    private static String familyValueOf(ComponentStyle style, String legacyFamily) {
        return style.fontFamily.isEmpty() ? legacyFamily : style.fontFamily;
    }

    private static int sizeOf(ComponentStyle style, int legacySize) {
        if (style.fontSize > 0) {
            return style.fontSize;
        }
        return legacySize > 0 ? legacySize : DEFAULT_FONT_SIZE;
    }

    private static LocalizeValue styleLabel(@Nullable Integer fontStyle) {
        if (fontStyle == null) {
            return LocalizeValue.empty();
        }
        return LocalizeValue.localizeTODO(switch (fontStyle) {
            case Font.BOLD -> "Bold";
            case Font.ITALIC -> "Italic";
            case Font.BOLD | Font.ITALIC -> "Bold Italic";
            default -> "Plain";
        });
    }
}
