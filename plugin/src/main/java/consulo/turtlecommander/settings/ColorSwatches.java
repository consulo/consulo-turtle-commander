package consulo.turtlecommander.settings;

import consulo.ui.color.ColorValue;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;

import java.awt.Color;

/**
 * The small rounded colour chip shown wherever settings presents a colour choice.
 *
 * <p>Drawn through {@link ImageEffects#canvas} rather than a hand-rolled {@code Icon}: the canvas
 * image reports {@code JBUI.scale}d dimensions and renders into a device-resolution bitmap, so the
 * swatch matches the size of neighbouring platform icons on a HiDPI display. It also caches the
 * rendered bitmap and re-renders on a scale or theme change.
 */
public final class ColorSwatches {
    private static final int SIZE = 14;
    private static final int ARC = 4;
    /** Inset so the chip doesn't bleed into the row's edge. */
    private static final int INSET = 1;

    private ColorSwatches() {
    }

    public static Image swatch(Color color) {
        ColorValue fill = TargetAWT.from(color);
        return ImageEffects.canvas(SIZE, SIZE, ctx -> {
            ctx.setFillStyle(fill);
            // roundRect only builds the path; fill() is what paints it.
            ctx.roundRect(INSET, INSET, SIZE - INSET * 2, SIZE - INSET * 2, ARC);
            ctx.fill();
        });
    }
}
