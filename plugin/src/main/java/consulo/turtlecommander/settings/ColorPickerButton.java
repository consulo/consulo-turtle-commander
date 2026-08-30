package consulo.turtlecommander.settings;

import consulo.localize.LocalizeValue;
import consulo.ui.ColorPickerBuilder;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.PopupStep;
import consulo.ui.image.Image;
import consulo.ui.style.StandardColors;
import org.jspecify.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A swatch button that opens the shared preset-colour popup.
 */
public class ColorPickerButton extends JButton {
    private static final int BUTTON_SIZE = 28;
    private static final String NO_COLOR_TEXT = "...";

    private @Nullable Color mySelectedColor;
    private @Nullable Runnable myOnChange;

    public ColorPickerButton() {
        super(NO_COLOR_TEXT);
        setPreferredSize(new Dimension(BUTTON_SIZE, BUTTON_SIZE));
        setFocusable(false);
        addActionListener(e -> showColorPopup(this, "Choose Color", mySelectedColor, this::setColor));
    }

    public void setOnChange(@Nullable Runnable onChange) {
        myOnChange = onChange;
    }

    public void setColor(@Nullable Color color) {
        mySelectedColor = color;
        setIcon(color != null ? TargetAWT.to(ColorSwatches.swatch(color)) : null);
        // The icon replaces the placeholder text, so a picked colour shows only the swatch.
        setText(color != null ? "" : NO_COLOR_TEXT);
        setToolTipText(color != null ? PresetColors.toHex(color) : "Default");
        if (myOnChange != null) {
            myOnChange.run();
        }
    }

    public @Nullable Color getColor() {
        return mySelectedColor;
    }

    public String getColorHex() {
        return PresetColors.toHex(mySelectedColor);
    }

    /**
     * The preset palette plus a "Custom..." entry that opens the platform colour chooser, so every
     * picker in settings looks the same. {@code onPick} receives null for the "None" preset.
     */
    public static void showColorPopup(JComponent anchor, String title, @Nullable Color current,
                                      Consumer<@Nullable Color> onPick) {
        // A trailing null stands for "Custom...", which has no preset hex.
        List<Map.Entry<String, String>> items = new ArrayList<>(PresetColors.PRESETS.entrySet());
        items.add(null);

        BaseListPopupStep<Map.Entry<String, String>> step =
            new BaseListPopupStep<Map.Entry<String, String>>(title, items) {
                @Override
                public String getTextFor(Map.Entry<String, String> value) {
                    return value != null ? value.getKey() : "Custom...";
                }

                @Override
                public @Nullable Image getIconFor(Map.Entry<String, String> value) {
                    Color color = value != null ? PresetColors.decode(value.getValue()) : null;
                    return color != null ? ColorSwatches.swatch(color) : null;
                }

                @Override
                public boolean isSpeedSearchEnabled() {
                    return true;
                }

                @Override
                public @Nullable PopupStep<?> onChosen(Map.Entry<String, String> selectedValue,
                                                       boolean finalChoice) {
                    if (selectedValue == null) {
                        return doFinalStep(() -> {
                            ColorPickerBuilder.create()
                                .withTitle(LocalizeValue.localizeTODO(title))
                                .withColor(current != null ? TargetAWT.from(current) : StandardColors.GRAY)
                                .showAsync(TargetAWT.wrap(anchor))
                                .whenCompleteAsync((colorValue, throwable) -> {
                                    if (colorValue != null) {
                                        onPick.accept(TargetAWT.to(colorValue));
                                    }
                                });
                        });
                    }
                    Color color = PresetColors.decode(selectedValue.getValue());
                    return doFinalStep(() -> onPick.accept(color));
                }
            };

        JBPopupFactory.getInstance().createListPopup(step).showUnderneathOf(anchor);
    }
}
