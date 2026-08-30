package consulo.turtlecommander.operation;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the {@code cd} form of the command bar's input. */
public final class CdCommand {
    private static final Pattern CD_PATTERN = Pattern.compile("^\\s*(?i:cd)(?:\\s+(.*?))?\\s*$");

    private CdCommand() {
    }

    /** The argument of a {@code cd} command, or null when the input isn't a cd at all. */
    public static @Nullable String parseArgument(String command) {
        Matcher matcher = CD_PATTERN.matcher(command);
        if (!matcher.find()) {
            return null;
        }
        String argument = matcher.group(1);
        return unquote(argument != null ? argument.trim() : "");
    }

    public static @Nullable Path resolveTarget(String arg, Path base, Path home, boolean windows) {
        try {
            // Backslashes are only path separators on Windows; elsewhere they're literal, so
            // normalise them away to keep `cd foo\bar` working the way users expect.
            String normalized = windows ? arg : arg.replace('\\', '/');

            if (normalized.isEmpty() || normalized.equals("~") || normalized.equals("~/")) {
                return home.normalize();
            }
            if (normalized.startsWith("~/")) {
                return home.resolve(normalized.substring(2)).normalize();
            }

            Path path = Path.of(normalized);
            Path resolved = path.isAbsolute() ? path : base.resolve(normalized);
            return resolved.normalize();
        }
        catch (Exception e) {
            return null;
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
