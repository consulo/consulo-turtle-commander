package consulo.turtlecommander.settings;

import java.time.Instant;
import java.time.ZoneId;

/**
 * One-line summaries of a matcher, shown in the rules table and the rule editor's matcher list.
 *
 * <p>Kept out of {@link RuleMatcher} itself so the model stays free of presentation concerns.
 */
public final class RuleMatcherDescriptions {
    private RuleMatcherDescriptions() {
    }

    public static String describe(RuleMatcher matcher) {
        return switch (matcher) {
            case RuleMatcher.Size size -> describeSize(size);
            case RuleMatcher.Name name -> "name " + patternKind(name.kind()) + " \"" + name.pattern() + "\""
                + scope(name.appliesTo()) + caseSuffix(name.caseSensitive());
            case RuleMatcher.Contains contains -> "contains " + patternKind(contains.kind())
                + " \"" + contains.pattern() + "\"" + caseSuffix(contains.caseSensitive());
            case RuleMatcher.Date date -> describeDate(date);
            case RuleMatcher.Text text -> textField(text.field()) + " " + patternKind(text.kind())
                + " \"" + text.pattern() + "\"" + caseSuffix(text.caseSensitive());
            case RuleMatcher.Symlink symlink -> switch (symlink.state()) {
                case ANY -> "symlink";
                case VALID -> "symlink (valid)";
                case BROKEN -> "symlink (broken)";
            };
            case RuleMatcher.NamedPipe ignored -> "named pipe";
        };
    }

    private static String describeSize(RuleMatcher.Size size) {
        String bytes = humanBytes(size.bytes());
        return switch (size.op()) {
            case LT -> "size < " + bytes;
            case LE -> "size \u2264 " + bytes;
            case EQ -> "size = " + bytes;
            case GE -> "size \u2265 " + bytes;
            case GT -> "size > " + bytes;
            case BETWEEN -> bytes + " \u2264 size \u2264 " + humanBytes(size.bytesMax());
        };
    }

    /**
     * Renders a threshold in the largest unit that divides it exactly, so a rule reads back in the
     * units it was authored in. Deliberately not the panel's size formatter, whose rounding would
     * turn an exact "5 MB" threshold into an approximation.
     */
    static String humanBytes(long bytes) {
        long gb = 1024L * 1024 * 1024;
        long mb = 1024L * 1024;
        long kb = 1024L;
        if (bytes >= gb && bytes % gb == 0) {
            return bytes / gb + " GB";
        }
        if (bytes >= mb && bytes % mb == 0) {
            return bytes / mb + " MB";
        }
        if (bytes >= kb && bytes % kb == 0) {
            return bytes / kb + " KB";
        }
        return bytes + " B";
    }

    private static String describeDate(RuleMatcher.Date date) {
        String field = date.field() == DateField.CREATED ? "created" : "modified";
        return switch (date.op()) {
            case BEFORE -> field + " before " + isoDate(date.epochMillis());
            case AFTER -> field + " on/after " + isoDate(date.epochMillis());
            case BETWEEN -> field + " " + isoDate(date.epochMillis()) + ".." + isoDate(date.epochMillisMax());
            case WITHIN_LAST -> field + " within last " + date.amount() + " " + date.unit().getLabel();
            case OLDER_THAN -> field + " older than " + date.amount() + " " + date.unit().getLabel();
        };
    }

    private static String patternKind(PatternKind kind) {
        return switch (kind) {
            case EXACT -> "=";
            case GLOB -> "glob";
            case REGEX -> "regex";
        };
    }

    private static String scope(AppliesTo appliesTo) {
        return switch (appliesTo) {
            case FILE -> " (files)";
            case DIR -> " (dirs)";
            case BOTH -> "";
        };
    }

    private static String textField(TextProperty field) {
        return switch (field) {
            case OWNER -> "owner";
            case GROUP -> "group";
            case PERMISSIONS -> "permissions";
        };
    }

    /** "Aa" is the conventional shorthand for a case-sensitive match. */
    private static String caseSuffix(boolean caseSensitive) {
        return caseSensitive ? ", Aa" : "";
    }

    /** Any time of day collapses to its calendar date, which is all a rule ever compares. */
    private static String isoDate(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }
}
