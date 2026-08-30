package consulo.turtlecommander.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Serializable mirror of {@link ColorRule}.
 *
 * <p>{@code fontStyle}/{@code fontSize} use a negative sentinel for "inherit" because the
 * serializer binds primitives, not boxed types. Only a {@code true} {@code strikethrough} is
 * meaningful — absent/false round-trips back to inherit.
 */
public class SavedColorRule {
    public String id = "";
    public String name = "";
    public int priority = 0;
    public boolean active = true;
    public String combinator = Combinator.AND.name();
    public List<SavedColorMatcher> matchers = new ArrayList<>();
    public String fontColor = "";
    public String backgroundColor = "";
    public String iconDotColor = "";
    public int fontStyle = -1;
    public int fontSize = -1;
    public boolean strikethrough = false;
    public String iconId = "";

    public ColorRule toRule() {
        List<RuleMatcher> ruleMatchers = new ArrayList<>();
        for (SavedColorMatcher saved : matchers) {
            RuleMatcher matcher = saved.toMatcher();
            if (matcher != null) {
                ruleMatchers.add(matcher);
            }
        }

        RuleStyle style = new RuleStyle(
            fontColor,
            backgroundColor,
            iconDotColor,
            fontStyle >= 0 ? fontStyle : null,
            fontSize > 0 ? fontSize : null,
            strikethrough ? Boolean.TRUE : null,
            iconId
        );

        Combinator parsedCombinator;
        try {
            parsedCombinator = Combinator.valueOf(combinator);
        }
        catch (IllegalArgumentException e) {
            parsedCombinator = Combinator.AND;
        }

        return new ColorRule(
            id.isEmpty() ? UUID.randomUUID().toString() : id,
            name,
            priority,
            active,
            parsedCombinator,
            ruleMatchers,
            style
        );
    }

    public static SavedColorRule fromRule(ColorRule rule) {
        SavedColorRule saved = new SavedColorRule();
        saved.id = rule.id();
        saved.name = rule.name();
        saved.priority = rule.priority();
        saved.active = rule.active();
        saved.combinator = rule.combinator().name();

        List<SavedColorMatcher> savedMatchers = new ArrayList<>();
        for (RuleMatcher matcher : rule.matchers()) {
            savedMatchers.add(SavedColorMatcher.fromMatcher(matcher));
        }
        saved.matchers = savedMatchers;

        RuleStyle style = rule.style();
        saved.fontColor = style.fontColor();
        saved.backgroundColor = style.backgroundColor();
        saved.iconDotColor = style.iconDotColor();
        saved.fontStyle = style.fontStyle() != null ? style.fontStyle() : -1;
        saved.fontSize = style.fontSize() != null ? style.fontSize() : -1;
        saved.strikethrough = Boolean.TRUE.equals(style.strikethrough());
        saved.iconId = style.iconId();
        return saved;
    }
}
