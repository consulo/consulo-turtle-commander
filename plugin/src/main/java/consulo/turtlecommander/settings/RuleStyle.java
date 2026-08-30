package consulo.turtlecommander.settings;

import org.jspecify.annotations.Nullable;

/**
 * Visual overrides a matching {@link ColorRule} applies. Empty string / null mean "inherit".
 *
 * @param strikethrough null inherits, true strikes out the filename (e.g. broken links, backups)
 * @param iconId        stable {@code RuleIcons} key for a rule-chosen icon replacing the
 *                      file-type/folder icon. A key we own rather than a platform icon path, so
 *                      saved rules survive IDE upgrades; unknown keys fall back to no override.
 */
public record RuleStyle(
    String fontColor,
    String backgroundColor,
    String iconDotColor,
    @Nullable Integer fontStyle,
    @Nullable Integer fontSize,
    @Nullable Boolean strikethrough,
    String iconId
) {
    public static final RuleStyle EMPTY = new RuleStyle("", "", "", null, null, null, "");

    public boolean isEmpty() {
        return fontColor.isEmpty() && backgroundColor.isEmpty() && iconDotColor.isEmpty()
            && fontStyle == null && fontSize == null && strikethrough == null && iconId.isEmpty();
    }

    /** Overlay {@code other} on this style: {@code other}'s set fields win. */
    public RuleStyle overlay(RuleStyle other) {
        return new RuleStyle(
            other.fontColor.isEmpty() ? fontColor : other.fontColor,
            other.backgroundColor.isEmpty() ? backgroundColor : other.backgroundColor,
            other.iconDotColor.isEmpty() ? iconDotColor : other.iconDotColor,
            other.fontStyle != null ? other.fontStyle : fontStyle,
            other.fontSize != null ? other.fontSize : fontSize,
            other.strikethrough != null ? other.strikethrough : strikethrough,
            other.iconId.isEmpty() ? iconId : other.iconId
        );
    }
}
