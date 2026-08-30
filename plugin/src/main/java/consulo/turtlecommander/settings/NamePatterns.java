package consulo.turtlecommander.settings;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compiles the EXACT / GLOB / REGEX pattern kinds shared by the name, contains and text matchers.
 */
public final class NamePatterns {
    private NamePatterns() {
    }

    /** Returns null for an empty pattern or one that fails to compile — such a matcher never matches. */
    public static @Nullable Predicate<String> compile(PatternKind kind, String pattern, boolean caseSensitive) {
        if (pattern.isEmpty()) {
            return null;
        }
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        switch (kind) {
            case EXACT -> {
                if (caseSensitive) {
                    return pattern::equals;
                }
                String target = pattern.toLowerCase(Locale.ROOT);
                return name -> name.toLowerCase(Locale.ROOT).equals(target);
            }
            case GLOB -> {
                return compilePattern(globToRegex(pattern), flags);
            }
            case REGEX -> {
                return compilePattern(pattern, flags);
            }
            default -> {
                return null;
            }
        }
    }

    private static @Nullable Predicate<String> compilePattern(String regex, int flags) {
        try {
            Pattern compiled = Pattern.compile(regex, flags);
            return name -> compiled.matcher(name).matches();
        }
        catch (PatternSyntaxException e) {
            return null;
        }
    }

    /**
     * Translates a simple glob ({@code *}, {@code ?}, {@code [...]}) into a full-match regex.
     * Globs here apply to filenames only — path separators have no special meaning.
     */
    public static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                case '[' -> {
                    int end = glob.indexOf(']', i + 1);
                    if (end < 0) {
                        sb.append("\\[");
                    }
                    else {
                        sb.append('[');
                        int j = i + 1;
                        if (j < end && glob.charAt(j) == '!') {
                            sb.append('^');
                            j++;
                        }
                        while (j < end) {
                            char ch = glob.charAt(j);
                            // `&` is a regex-class metacharacter only when doubled (`&&` is
                            // intersection in Java regex). Inside a glob `[...]` it always means
                            // literal, so escape every occurrence to rule the doubled case out.
                            if (ch == '\\' || ch == ']' || ch == '&') {
                                sb.append('\\');
                            }
                            sb.append(ch);
                            j++;
                        }
                        sb.append(']');
                        i = end;
                    }
                }
                case '\\' -> {
                    if (i + 1 < glob.length()) {
                        sb.append(Pattern.quote(String.valueOf(glob.charAt(i + 1))));
                        i++;
                    }
                    else {
                        sb.append("\\\\");
                    }
                }
                case '.', '(', ')', '+', '|', '^', '$', '{', '}' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
            i++;
        }
        sb.append('$');
        return sb.toString();
    }
}
