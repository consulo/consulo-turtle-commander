package consulo.turtlecommander.ui;

import consulo.platform.base.icon.PlatformIconGroup;
import consulo.turtlecommander.settings.PresetColors;
import consulo.turtlecommander.settings.RuleIcons;
import consulo.ui.color.ColorValue;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;

import java.awt.Color;

/** How a favourite is drawn in the tool window header and the settings list. */
public final class FavoriteIcons {
    private static final int SIZE = Image.DEFAULT_ICON_SIZE;
    private static final int TAB_WIDTH = 6;
    private static final int TAB_HEIGHT = 3;

    private FavoriteIcons() {
    }

    /**
     * A chosen icon takes over the glyph and the colour becomes a corner dot; with only a colour it
     * is a tinted folder, and with neither the plain folder icon.
     */
    public static Image favoriteIcon(String colorHex, String iconKey) {
        Color color = PresetColors.decode(colorHex);
        Image customIcon = RuleIcons.resolve(iconKey);

        if (customIcon != null) {
            return color != null ? DirectoryIcons.iconWithDot(customIcon, color) : customIcon;
        }
        return color != null ? colorFolder(color) : PlatformIconGroup.nodesFolder();
    }

    /** A folder glyph drawn in a single colour, so a favourite reads at a glance. */
    private static Image colorFolder(Color color) {
        ColorValue fill = TargetAWT.from(color);
        return ImageEffects.canvas(SIZE, SIZE, ctx -> {
            ctx.setFillStyle(fill);
            // The tab sits above and left of the body, which starts two rows lower.
            ctx.roundRect(1, 2, TAB_WIDTH, TAB_HEIGHT + 1, 2);
            ctx.fill();
            ctx.roundRect(1, 4, SIZE - 2, SIZE - 6, 3);
            ctx.fill();
        });
    }
}
