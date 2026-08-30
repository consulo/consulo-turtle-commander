package consulo.turtlecommander.settings;

import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The swatch palette offered by every colour picker in settings, in display order. */
public final class PresetColors {
    /** Label to hex; the empty hex is the "no colour" entry. */
    public static final Map<String, String> PRESETS = buildPresets();

    private PresetColors() {
    }

    private static Map<String, String> buildPresets() {
        Map<String, String> presets = new LinkedHashMap<>();
        presets.put("None", "");
        presets.put("Blue", "#5B9BD5");
        presets.put("Green", "#70AD47");
        presets.put("Orange", "#ED7D31");
        presets.put("Red", "#E06666");
        presets.put("Purple", "#A855F7");
        presets.put("Teal", "#4DB6AC");
        presets.put("Brown", "#A0522D");
        presets.put("Pink", "#F48FB1");
        presets.put("Gray", "#9E9E9E");
        // Insertion order is the display order, so this can't be Map.copyOf.
        return Collections.unmodifiableMap(presets);
    }

    /** Null for a blank or unparseable hex, so a corrupt setting degrades to "no colour". */
    public static @Nullable Color decode(String hex) {
        if (hex.isBlank()) {
            return null;
        }
        try {
            return Color.decode(hex);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    public static String toHex(@Nullable Color color) {
        return color == null
            ? ""
            : String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}
