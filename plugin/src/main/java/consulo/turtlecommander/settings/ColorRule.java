package consulo.turtlecommander.settings;

import consulo.turtlecommander.model.FileEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ColorRule(
    String id,
    String name,
    int priority,
    boolean active,
    Combinator combinator,
    List<RuleMatcher> matchers,
    RuleStyle style
) {
    public static ColorRule of(String name, Combinator combinator, List<RuleMatcher> matchers, RuleStyle style) {
        return new ColorRule(UUID.randomUUID().toString(), name, 0, true, combinator, matchers, style);
    }

    public boolean matches(FileEntry entry, ContainsEvaluator contains) {
        if (matchers.isEmpty()) {
            return false;
        }

        List<RuleMatcher> ordered = matchers;
        if (matchers.size() > 1) {
            ordered = new ArrayList<>(matchers);
            ordered.sort(Comparator.comparingInt(RuleMatcher::cost));
        }

        if (combinator == Combinator.AND) {
            for (RuleMatcher matcher : ordered) {
                if (!matcher.matches(entry, contains)) {
                    return false;
                }
            }
            return true;
        }

        for (RuleMatcher matcher : ordered) {
            if (matcher.matches(entry, contains)) {
                return true;
            }
        }
        return false;
    }
}
