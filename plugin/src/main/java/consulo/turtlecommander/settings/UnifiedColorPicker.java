package consulo.turtlecommander.settings;

import consulo.localize.LocalizeValue;
import consulo.ui.Button;
import consulo.ui.ButtonStyle;
import consulo.ui.ColorPickerBuilder;
import consulo.ui.Component;
import consulo.ui.MenuItem;
import consulo.ui.PopupMenu;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.style.StandardColors;
import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A button showing a color, which opens the preset palette plus "Custom..." for the platform color chooser - so every
 * picker in settings looks the same, on every frontend. No color is the "None" preset.
 */
public final class UnifiedColorPicker {
    private static final LocalizeValue NO_COLOR_TEXT = LocalizeValue.of("...");

    private final Button myButton = Button.create(NO_COLOR_TEXT);
    private @Nullable Color mySelectedColor;
    private @Nullable Runnable myOnChange;

    @RequiredUIAccess
    public UnifiedColorPicker() {
        myButton.addStyle(ButtonStyle.BORDERLESS);
        myButton.addClickListener(event -> showColorPopup(myButton, "Choose Color", mySelectedColor, this::setColor));
        setColor(null);
    }

    public Component getComponent() {
        return myButton;
    }

    public void setOnChange(@Nullable Runnable onChange) {
        myOnChange = onChange;
    }

    @RequiredUIAccess
    public void setColor(@Nullable Color color) {
        mySelectedColor = color;
        // the swatch replaces the placeholder text, so a picked color shows only the swatch
        myButton.setIcon(color != null ? ColorSwatches.swatch(color) : null);
        myButton.setText(color != null ? LocalizeValue.empty() : NO_COLOR_TEXT);
        myButton.setToolTipText(LocalizeValue.of(color != null ? PresetColors.toHex(color) : "Default"));
        Runnable onChange = myOnChange;
        if (onChange != null) {
            onChange.run();
        }
    }

    public @Nullable Color getColor() {
        return mySelectedColor;
    }

    public String getColorHex() {
        return PresetColors.toHex(mySelectedColor);
    }

    /** The preset palette and "Custom...", under {@code anchor}; {@code onPick} receives null for "None". */
    @RequiredUIAccess
    public static void showColorPopup(Component anchor, String title, @Nullable Color current, Consumer<@Nullable Color> onPick) {
        PopupMenu menu = PopupMenu.create(anchor);
        for (Map.Entry<String, String> preset : PresetColors.PRESETS.entrySet()) {
            Color color = PresetColors.decode(preset.getValue());
            MenuItem item = MenuItem.create(LocalizeValue.of(preset.getKey()));
            item.setIcon(color != null ? ColorSwatches.swatch(color) : null);
            item.addClickListener(event -> onPick.accept(color));
            menu.add(item);
        }
        MenuItem custom = MenuItem.create(LocalizeValue.localizeTODO("Custom..."));
        custom.addClickListener(event -> ColorPickerBuilder.create()
            .withTitle(LocalizeValue.localizeTODO(title))
            .withColor(current != null ? TargetAWT.from(current) : StandardColors.GRAY)
            .showAsync(anchor)
            .whenComplete((colorValue, error) -> {
                if (colorValue != null) {
                    onPick.accept(TargetAWT.to(colorValue));
                }
            }));
        menu.add(custom);
        menu.show(0, 0);
    }
}
