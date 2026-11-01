package consulo.turtlecommander.dialog;

import consulo.turtlecommander.service.OverwritePolicy;
import org.jspecify.annotations.Nullable;

/**
 * User-facing labels for the {@link OverwritePolicy} combo in the copy / move / extract dialogs and settings.
 * Centralised so every call site uses identical wording.
 */
public final class OverwritePolicyRenderer {
    private OverwritePolicyRenderer() {
    }

    public static String label(@Nullable OverwritePolicy policy) {
        if (policy == null) {
            return "";
        }
        return switch (policy) {
            case ASK -> "Ask for each existing file";
            case OVERWRITE_ALL -> "Overwrite all";
            case SKIP_ALL -> "Skip all existing";
            case IF_OLDER -> "Overwrite if older";
        };
    }
}
