package consulo.turtlecommander.settings;

import org.jspecify.annotations.Nullable;

/**
 * Style produced by resolving a {@link consulo.turtlecommander.model.FileEntry} against the active
 * {@link ColorRule} list. Empty string / null fields mean "inherit" — the renderer falls through
 * to its existing defaults (LAF, {@link ComponentStyle}, or column-level styling).
 */
public record ResolvedStyle(
    String fontColor,
    String backgroundColor,
    String iconDotColor,
    @Nullable Integer fontStyle,
    @Nullable Integer fontSize,
    @Nullable Boolean strikethrough,
    String iconId
) {
    public static final ResolvedStyle EMPTY = new ResolvedStyle("", "", "", null, null, null, "");

    public boolean isEmpty() {
        return fontColor.isEmpty() && backgroundColor.isEmpty() && iconDotColor.isEmpty()
            && fontStyle == null && fontSize == null && strikethrough == null && iconId.isEmpty();
    }

    public static ResolvedStyle fromRuleStyle(RuleStyle style) {
        return new ResolvedStyle(style.fontColor(), style.backgroundColor(), style.iconDotColor(),
            style.fontStyle(), style.fontSize(), style.strikethrough(), style.iconId());
    }
}
