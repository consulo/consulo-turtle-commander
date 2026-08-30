package consulo.turtlecommander.settings;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Export / import of a {@link ColorRulesConfig} using the same {@code key=value} text format that
 * {@link ThemeManager} uses for themes, so the two share UX and serialization conventions.
 *
 * <pre>
 * name=&lt;config name&gt;
 * rule.0.name=&lt;rule name&gt;
 * rule.0.priority=10
 * rule.0.combinator=AND
 * rule.0.fontColor=#112233
 * rule.0.matcher.0.type=CONTAINS
 * rule.0.matcher.0.patternKind=EXACT
 * rule.0.matcher.0.pattern=.git
 * </pre>
 *
 * <p>Only non-default style/matcher fields are emitted. Unknown keys are ignored on import, and a
 * rule missing a name is dropped silently rather than failing the whole import.
 */
public final class ColorRulesIO {
    private static final Pattern RULE_KEY = Pattern.compile("^rule\\.(\\d+)\\..+$");

    private ColorRulesIO() {
    }

    public static String export(ColorRulesConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("name=").append(config.name()).append('\n');
        List<ColorRule> rules = config.rules();
        for (int i = 0; i < rules.size(); i++) {
            appendRule(sb, i, rules.get(i));
        }
        return sb.toString();
    }

    public static @Nullable ColorRulesConfig parse(String text) {
        Map<String, String> props = parseProps(text);
        String name = props.get("name");
        if (name == null || name.isBlank()) {
            return null;
        }

        TreeSet<Integer> ruleIndices = new TreeSet<>();
        for (String key : props.keySet()) {
            Matcher matcher = RULE_KEY.matcher(key);
            if (matcher.matches()) {
                Integer index = parseInteger(matcher.group(1));
                if (index != null) {
                    ruleIndices.add(index);
                }
            }
        }

        List<ColorRule> rules = new ArrayList<>();
        for (Integer index : ruleIndices) {
            ColorRule rule = parseRule(props, index);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return new ColorRulesConfig(name, rules);
    }

    private static Map<String, String> parseProps(String text) {
        Map<String, String> props = new HashMap<>();
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                continue;
            }
            props.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }
        return props;
    }

    private static void appendRule(StringBuilder sb, int index, ColorRule rule) {
        String prefix = "rule." + index;
        if (!rule.id().isEmpty()) {
            sb.append(prefix).append(".id=").append(rule.id()).append('\n');
        }
        sb.append(prefix).append(".name=").append(rule.name()).append('\n');
        if (rule.priority() != 0) {
            sb.append(prefix).append(".priority=").append(rule.priority()).append('\n');
        }
        if (!rule.active()) {
            sb.append(prefix).append(".active=false\n");
        }
        sb.append(prefix).append(".combinator=").append(rule.combinator().name()).append('\n');
        appendRuleStyle(sb, prefix, rule.style());

        List<RuleMatcher> matchers = rule.matchers();
        for (int i = 0; i < matchers.size(); i++) {
            appendMatcher(sb, prefix + ".matcher." + i, matchers.get(i));
        }
    }

    private static void appendRuleStyle(StringBuilder sb, String prefix, RuleStyle style) {
        appendIfSet(sb, prefix, "fontColor", style.fontColor());
        appendIfSet(sb, prefix, "backgroundColor", style.backgroundColor());
        appendIfSet(sb, prefix, "iconDotColor", style.iconDotColor());
        if (style.fontStyle() != null) {
            sb.append(prefix).append(".fontStyle=").append(style.fontStyle()).append('\n');
        }
        if (style.fontSize() != null) {
            sb.append(prefix).append(".fontSize=").append(style.fontSize()).append('\n');
        }
        if (Boolean.TRUE.equals(style.strikethrough())) {
            sb.append(prefix).append(".strikethrough=true\n");
        }
        appendIfSet(sb, prefix, "iconId", style.iconId());
    }

    private static void appendMatcher(StringBuilder sb, String prefix, RuleMatcher matcher) {
        switch (matcher) {
            case RuleMatcher.Size size -> {
                sb.append(prefix).append(".type=SIZE\n");
                sb.append(prefix).append(".sizeOp=").append(size.op().name()).append('\n');
                sb.append(prefix).append(".sizeBytes=").append(size.bytes()).append('\n');
                if (size.bytesMax() != 0L) {
                    sb.append(prefix).append(".sizeBytesMax=").append(size.bytesMax()).append('\n');
                }
            }
            case RuleMatcher.Name name -> {
                sb.append(prefix).append(".type=NAME\n");
                sb.append(prefix).append(".patternKind=").append(name.kind().name()).append('\n');
                sb.append(prefix).append(".pattern=").append(name.pattern()).append('\n');
                if (name.caseSensitive()) {
                    sb.append(prefix).append(".caseSensitive=true\n");
                }
                if (name.appliesTo() != AppliesTo.BOTH) {
                    sb.append(prefix).append(".appliesTo=").append(name.appliesTo().name()).append('\n');
                }
            }
            case RuleMatcher.Contains contains -> {
                sb.append(prefix).append(".type=CONTAINS\n");
                sb.append(prefix).append(".patternKind=").append(contains.kind().name()).append('\n');
                sb.append(prefix).append(".pattern=").append(contains.pattern()).append('\n');
                if (contains.caseSensitive()) {
                    sb.append(prefix).append(".caseSensitive=true\n");
                }
            }
            case RuleMatcher.Date date -> {
                sb.append(prefix).append(".type=DATE\n");
                sb.append(prefix).append(".dateField=").append(date.field().name()).append('\n');
                sb.append(prefix).append(".dateOp=").append(date.op().name()).append('\n');
                if (date.epochMillis() != 0L) {
                    sb.append(prefix).append(".dateEpochMillis=").append(date.epochMillis()).append('\n');
                }
                if (date.epochMillisMax() != 0L) {
                    sb.append(prefix).append(".dateEpochMillisMax=").append(date.epochMillisMax()).append('\n');
                }
                if (date.amount() != 0L) {
                    sb.append(prefix).append(".dateAmount=").append(date.amount()).append('\n');
                }
                sb.append(prefix).append(".dateUnit=").append(date.unit().name()).append('\n');
            }
            case RuleMatcher.Text text -> {
                sb.append(prefix).append(".type=TEXT\n");
                sb.append(prefix).append(".textProperty=").append(text.field().name()).append('\n');
                sb.append(prefix).append(".patternKind=").append(text.kind().name()).append('\n');
                sb.append(prefix).append(".pattern=").append(text.pattern()).append('\n');
                if (text.caseSensitive()) {
                    sb.append(prefix).append(".caseSensitive=true\n");
                }
            }
            case RuleMatcher.Symlink symlink -> {
                sb.append(prefix).append(".type=SYMLINK\n");
                sb.append(prefix).append(".symlinkState=").append(symlink.state().name()).append('\n');
            }
            case RuleMatcher.NamedPipe ignored -> sb.append(prefix).append(".type=NAMED_PIPE\n");
        }
    }

    private static @Nullable ColorRule parseRule(Map<String, String> props, int index) {
        String prefix = "rule." + index;
        String name = props.get(prefix + ".name");
        if (name == null || name.isBlank()) {
            return null;
        }

        String id = props.get(prefix + ".id");
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        int priority = parseInt(props.get(prefix + ".priority"), 0);
        // Absent means active; any present value other than "true" means inactive.
        String activeValue = props.get(prefix + ".active");
        boolean active = activeValue == null || activeValue.equalsIgnoreCase("true");
        Combinator combinator = parseEnum(Combinator.class, props.get(prefix + ".combinator"), Combinator.AND);

        String matcherPrefix = prefix + ".matcher.";
        TreeSet<Integer> matcherIndices = new TreeSet<>();
        for (String key : props.keySet()) {
            if (!key.startsWith(matcherPrefix)) {
                continue;
            }
            String rest = key.substring(matcherPrefix.length());
            int dot = rest.indexOf('.');
            if (dot < 0) {
                continue;
            }
            Integer parsed = parseInteger(rest.substring(0, dot));
            if (parsed != null) {
                matcherIndices.add(parsed);
            }
        }

        List<RuleMatcher> matchers = new ArrayList<>();
        for (Integer matcherIndex : matcherIndices) {
            RuleMatcher matcher = parseMatcher(props, matcherPrefix + matcherIndex);
            if (matcher != null) {
                matchers.add(matcher);
            }
        }

        RuleStyle style = new RuleStyle(
            props.getOrDefault(prefix + ".fontColor", ""),
            props.getOrDefault(prefix + ".backgroundColor", ""),
            props.getOrDefault(prefix + ".iconDotColor", ""),
            parseInteger(props.get(prefix + ".fontStyle")),
            parseInteger(props.get(prefix + ".fontSize")),
            "true".equalsIgnoreCase(props.get(prefix + ".strikethrough")) ? Boolean.TRUE : null,
            props.getOrDefault(prefix + ".iconId", "")
        );

        return new ColorRule(id, name, priority, active, combinator, matchers, style);
    }

    private static @Nullable RuleMatcher parseMatcher(Map<String, String> props, String prefix) {
        String type = props.get(prefix + ".type");
        if (type == null) {
            return null;
        }

        switch (type) {
            case "SIZE" -> {
                SizeOp op = parseEnumOrNull(SizeOp.class, props.get(prefix + ".sizeOp"));
                Long bytes = parseLong(props.get(prefix + ".sizeBytes"));
                if (op == null || bytes == null) {
                    return null;
                }
                return new RuleMatcher.Size(op, bytes, parseLongOr(props.get(prefix + ".sizeBytesMax"), 0L));
            }
            case "NAME" -> {
                PatternKind kind = parseEnumOrNull(PatternKind.class, props.get(prefix + ".patternKind"));
                String pattern = props.get(prefix + ".pattern");
                if (kind == null || pattern == null) {
                    return null;
                }
                return new RuleMatcher.Name(kind, pattern, isTrue(props.get(prefix + ".caseSensitive")),
                    parseEnum(AppliesTo.class, props.get(prefix + ".appliesTo"), AppliesTo.BOTH));
            }
            case "CONTAINS" -> {
                PatternKind kind = parseEnumOrNull(PatternKind.class, props.get(prefix + ".patternKind"));
                String pattern = props.get(prefix + ".pattern");
                if (kind == null || pattern == null) {
                    return null;
                }
                return new RuleMatcher.Contains(kind, pattern, isTrue(props.get(prefix + ".caseSensitive")));
            }
            case "DATE" -> {
                DateField field = parseEnumOrNull(DateField.class, props.get(prefix + ".dateField"));
                DateOp op = parseEnumOrNull(DateOp.class, props.get(prefix + ".dateOp"));
                if (field == null || op == null) {
                    return null;
                }
                return new RuleMatcher.Date(field, op,
                    parseLongOr(props.get(prefix + ".dateEpochMillis"), 0L),
                    parseLongOr(props.get(prefix + ".dateEpochMillisMax"), 0L),
                    parseLongOr(props.get(prefix + ".dateAmount"), 0L),
                    parseEnum(DateUnit.class, props.get(prefix + ".dateUnit"), DateUnit.DAYS));
            }
            case "TEXT" -> {
                TextProperty field = parseEnumOrNull(TextProperty.class, props.get(prefix + ".textProperty"));
                PatternKind kind = parseEnumOrNull(PatternKind.class, props.get(prefix + ".patternKind"));
                String pattern = props.get(prefix + ".pattern");
                if (field == null || kind == null || pattern == null) {
                    return null;
                }
                return new RuleMatcher.Text(field, kind, pattern, isTrue(props.get(prefix + ".caseSensitive")));
            }
            case "SYMLINK" -> {
                return new RuleMatcher.Symlink(
                    parseEnum(SymlinkState.class, props.get(prefix + ".symlinkState"), SymlinkState.ANY));
            }
            case "NAMED_PIPE" -> {
                return RuleMatcher.NamedPipe.INSTANCE;
            }
            default -> {
                return null;
            }
        }
    }

    private static void appendIfSet(StringBuilder sb, String prefix, String key, String value) {
        if (!value.isEmpty()) {
            sb.append(prefix).append('.').append(key).append('=').append(value).append('\n');
        }
    }

    private static boolean isTrue(@Nullable String value) {
        return "true".equalsIgnoreCase(value);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, @Nullable String name, E fallback) {
        E parsed = parseEnumOrNull(type, name);
        return parsed != null ? parsed : fallback;
    }

    private static <E extends Enum<E>> @Nullable E parseEnumOrNull(Class<E> type, @Nullable String name) {
        if (name == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, name);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable Integer parseInteger(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseInt(@Nullable String value, int fallback) {
        Integer parsed = parseInteger(value);
        return parsed != null ? parsed : fallback;
    }

    private static @Nullable Long parseLong(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    private static long parseLongOr(@Nullable String value, long fallback) {
        Long parsed = parseLong(value);
        return parsed != null ? parsed : fallback;
    }
}
