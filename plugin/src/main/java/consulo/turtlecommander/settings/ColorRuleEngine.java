package consulo.turtlecommander.settings;

import consulo.turtlecommander.model.FileEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ColorRuleEngine {
    private static final Comparator<ColorRule> BY_PRIORITY_THEN_NAME =
        Comparator.comparingInt(ColorRule::priority)
            .thenComparing(rule -> rule.name().toLowerCase(Locale.ROOT));

    private ColorRuleEngine() {
    }

    /**
     * Evaluates {@code rules} against {@code entry} and returns the effective style.
     *
     * <p>In {@link ColorizationMode#WINNER} the highest-priority matching active rule wins and
     * supplies the style outright; ties are broken by name (case-insensitive).
     *
     * <p>In {@link ColorizationMode#LAYERED} every active matching rule contributes: rules are
     * overlaid in ascending priority so higher-priority rules override lower ones per field. A
     * single field may come from a different rule than its neighbour.
     */
    public static ResolvedStyle resolve(List<ColorRule> rules, FileEntry entry, ColorizationMode mode,
                                        ContainsEvaluator contains) {
        if (rules.isEmpty()) {
            return ResolvedStyle.EMPTY;
        }

        List<ColorRule> matching = new ArrayList<>();
        for (ColorRule rule : rules) {
            if (rule.active() && rule.matches(entry, contains)) {
                matching.add(rule);
            }
        }
        if (matching.isEmpty()) {
            return ResolvedStyle.EMPTY;
        }

        if (mode == ColorizationMode.WINNER) {
            ColorRule best = matching.get(0);
            for (ColorRule rule : matching) {
                if (BY_PRIORITY_THEN_NAME.compare(rule, best) > 0) {
                    best = rule;
                }
            }
            return ResolvedStyle.fromRuleStyle(best.style());
        }

        matching.sort(BY_PRIORITY_THEN_NAME);
        RuleStyle accumulated = RuleStyle.EMPTY;
        for (ColorRule rule : matching) {
            accumulated = accumulated.overlay(rule.style());
        }
        return ResolvedStyle.fromRuleStyle(accumulated);
    }

    public static ResolvedStyle resolve(List<ColorRule> rules, FileEntry entry) {
        return resolve(rules, entry, ColorizationMode.WINNER, ContainsEvaluator.EMPTY);
    }
}
