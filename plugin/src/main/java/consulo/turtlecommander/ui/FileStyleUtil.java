package consulo.turtlecommander.ui;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.service.ColorizationService;
import consulo.turtlecommander.settings.ResolvedStyle;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.speedSearch.SpeedSearchSupply;
import consulo.ui.ex.awt.util.ColorUtil;
import consulo.util.lang.StringUtil;
import consulo.application.util.matcher.MatcherTextRange;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Colour-rule and speed-search styling shared by the four view renderers. */
public final class FileStyleUtil {
    private static final Map<TextAttribute, Object> STRIKE_FONT_ATTRS =
        Map.of(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);

    private FileStyleUtil() {
    }

    /** The rule engine's effective style for an entry, respecting the tab-level master switch. */
    public static ResolvedStyle resolvedStyleFor(FileEntry entry, boolean enableHighlighting) {
        return enableHighlighting ? ColorizationService.getInstance().resolve(entry) : ResolvedStyle.EMPTY;
    }

    public static @Nullable Color fontColorOf(ResolvedStyle style) {
        return parseHexColor(style.fontColor());
    }

    public static @Nullable Color backgroundColorOf(ResolvedStyle style) {
        return parseHexColor(style.backgroundColor());
    }

    public static @Nullable Color iconDotColorOf(ResolvedStyle style) {
        return parseHexColor(style.iconDotColor());
    }

    /**
     * Coloured-text attributes for a filename cell, OR-ing in strikeout when the rule asks for it.
     * {@code baseStyle} is a {@link SimpleTextAttributes} bitmask — the {@code Font.PLAIN/BOLD/ITALIC}
     * bits line up with {@code STYLE_PLAIN/BOLD/ITALIC}.
     */
    public static SimpleTextAttributes nameTextAttributes(int baseStyle, @Nullable Color fg,
                                                          @Nullable Boolean strikethrough) {
        int bits = Boolean.TRUE.equals(strikethrough)
            ? baseStyle | SimpleTextAttributes.STYLE_STRIKEOUT
            : baseStyle;
        return new SimpleTextAttributes(bits, fg);
    }

    /** {@code base} with a strikethrough applied, for the label-based thumbnail renderer. */
    public static Font strikeFont(Font base, boolean strikethrough) {
        return strikethrough ? base.deriveFont(STRIKE_FONT_ATTRS) : base;
    }

    /**
     * Wraps matched speed-search substrings in an HTML span so they appear highlighted in
     * JLabel-based renderers, which have no equivalent of the coloured-component path. Returns the
     * text unchanged when no speed search is active or nothing matches.
     */
    public static String highlightSpeedSearchMatches(JComponent speedSearchComponent, String text) {
        SpeedSearchSupply supply = SpeedSearchSupply.getSupply(speedSearchComponent);
        if (supply == null) {
            return text;
        }
        Iterable<MatcherTextRange> fragments = supply.matchingFragments(text);
        if (fragments == null) {
            return text;
        }

        List<MatcherTextRange> ordered = new ArrayList<>();
        fragments.forEach(ordered::add);
        if (ordered.isEmpty()) {
            return text;
        }
        ordered.sort(Comparator.comparingInt(MatcherTextRange::getStartOffset));

        Color bg = firstNonNull(UIManager.getColor("SearchMatch.startBackground"),
            UIManager.getColor("SpeedSearch.background"),
            new JBColor(new Color(0xFFC800), new Color(0x5E5339)));
        Color fg = firstNonNull(UIManager.getColor("SearchMatch.foreground"),
            UIManager.getColor("SpeedSearch.foreground"), null);

        StringBuilder style = new StringBuilder();
        style.append("background:").append(ColorUtil.toHex(bg)).append(';');
        if (fg != null) {
            style.append("color:").append(ColorUtil.toHex(fg)).append(';');
        }

        StringBuilder sb = new StringBuilder("<html>");
        int cursor = 0;
        for (MatcherTextRange range : ordered) {
            int start = clamp(range.getStartOffset(), 0, text.length());
            int end = clamp(range.getEndOffset(), start, text.length());
            if (start > cursor) {
                sb.append(StringUtil.escapeXmlEntities(text.substring(cursor, start)));
            }
            sb.append("<span style='").append(style).append("'>");
            sb.append(StringUtil.escapeXmlEntities(text.substring(start, end)));
            sb.append("</span>");
            cursor = end;
        }
        if (cursor < text.length()) {
            sb.append(StringUtil.escapeXmlEntities(text.substring(cursor)));
        }
        sb.append("</html>");
        return sb.toString();
    }

    private static @Nullable Color parseHexColor(String hex) {
        if (hex.isBlank()) {
            return null;
        }
        try {
            return Color.decode(hex);
        }
        catch (Exception e) {
            return null;
        }
    }

    private static @Nullable Color firstNonNull(@Nullable Color a, @Nullable Color b, @Nullable Color c) {
        if (a != null) {
            return a;
        }
        return b != null ? b : c;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
