package consulo.turtlecommander.util;

import consulo.turtlecommander.settings.TurtleCommanderSettings;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Caches the {@link DateTimeFormatter} built from the user-configured pattern so per-cell
 * rendering in the file table doesn't recompile the pattern on every paint.
 *
 * <p>The setting is re-read on each call so a Configurable apply() is picked up without an
 * explicit invalidation, but the formatter is only rebuilt when the pattern actually changed.
 * Invalid patterns silently fall back to the default. Headless callers get the default formatter
 * when the application service isn't available.
 */
public final class DateTimeFormatters {
    private static final DateTimeFormatter DEFAULT_FORMATTER =
        DateTimeFormatter.ofPattern(TurtleCommanderSettings.DEFAULT_DATE_TIME_FORMAT).withZone(ZoneId.systemDefault());

    private static volatile String ourCachedPattern = TurtleCommanderSettings.DEFAULT_DATE_TIME_FORMAT;
    private static volatile DateTimeFormatter ourCachedFormatter = DEFAULT_FORMATTER;

    private DateTimeFormatters() {
    }

    public static DateTimeFormatter current() {
        String pattern;
        try {
            pattern = TurtleCommanderSettings.getInstance().getDateTimeFormat();
        }
        catch (Exception e) {
            pattern = TurtleCommanderSettings.DEFAULT_DATE_TIME_FORMAT;
        }

        if (pattern.equals(ourCachedPattern)) {
            return ourCachedFormatter;
        }

        DateTimeFormatter rebuilt;
        try {
            rebuilt = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());
        }
        catch (Exception e) {
            rebuilt = DEFAULT_FORMATTER;
        }

        ourCachedPattern = pattern;
        ourCachedFormatter = rebuilt;
        return rebuilt;
    }

    public static String format(long millis) {
        return current().format(Instant.ofEpochMilli(millis));
    }
}
