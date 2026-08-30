package consulo.turtlecommander.ui;

import consulo.ui.color.ColorValue;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;

import java.awt.Color;

public final class DirectoryIcons {
    private static final int DOT_SIZE = 5;

    private DirectoryIcons() {
    }

    /**
     * Overlays a coloured dot on {@code base} — any file-type, folder, or rule-chosen override icon.
     * The colour comes from the colorization rule engine.
     *
     * <p>Both images are composed on an {@link ImageEffects#canvas}, which handles the HiDPI scaling
     * and keeps the result a cross-platform {@link Image} — so callers no longer have to bounce
     * through AWT just to overlay a dot.
     */
    public static Image iconWithDot(Image base, Color color) {
        ColorValue dot = TargetAWT.from(color);
        // The canvas works in logical units, but Image.getWidth() is scaled in some implementations
        // and unscaled in others — so draw at the standard icon size explicitly rather than trusting
        // the base image's reported dimensions.
        int size = Image.DEFAULT_ICON_SIZE;
        double radius = DOT_SIZE / 2.0;

        return ImageEffects.canvas(size, size, ctx -> {
            ctx.drawImage(base, 0, 0, size, size);
            ctx.setFillStyle(dot);
            // Centred on the bottom-right corner, so the dot sits flush against it.
            ctx.arc(size - radius, size - radius, radius, 0, 2 * Math.PI);
            ctx.fill();
        });
    }
}
