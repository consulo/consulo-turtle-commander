package consulo.turtlecommander.search;

import org.jspecify.annotations.Nullable;

/**
 * A timestamp bound. The millis pairs describe day-resolution windows, so an "equals this day"
 * comparison is a range test rather than an exact instant match.
 *
 * @param text1 the user's raw input, kept so the dialog can round-trip what was typed
 */
public record DateFilter(DateFilterMode mode, long startMillis, long endMillis,
                         @Nullable Long startMillis2, @Nullable Long endMillis2,
                         String text1, @Nullable String text2) {
}
