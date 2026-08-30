package consulo.turtlecommander.operation;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Pure template engine for the Multi-Rename tool, mirroring the subset of Total Commander's Ctrl+M
 * placeholders we support. Everything here is side-effect free so it can be tested without a
 * filesystem.
 *
 * <p>Placeholders:
 * <ul>
 * <li>{@code [N]} base name; {@code [N a]}, {@code [N a-b]}, {@code [N a-]}, {@code [N a,n]}
 * substring forms (1-based, negative counts from the end)</li>
 * <li>{@code [E]} and the same substring forms, applied to the extension</li>
 * <li>{@code [C]}, {@code [C s]}, {@code [C s+step]}, {@code [C s+step:w]} counter</li>
 * <li>{@code [Y] [M] [D] [h] [n] [s]} date/time parts, {@code [YMD]} shorthand — {@code [n]} is
 * minute, avoiding the month/minute clash</li>
 * <li>{@code [P]} parent directory name, {@code [G]} grandparent</li>
 * <li>{@code [R]}, {@code [R n]}, {@code [R##…]} random digits; {@code [Ra n]} / {@code [RA n]} /
 * {@code [RM n]} random lower / upper / mixed alphanumerics</li>
 * </ul>
 *
 * <p>Unknown placeholders are left untouched, brackets included, so typos show up in the preview
 * instead of being silently eaten.
 */
public final class MultiRenameTemplate {
    public record CounterConfig(int start, int step, int width) {
        public static final CounterConfig DEFAULT = new CounterConfig(1, 1, 1);
    }

    public enum CaseMode {
        UNCHANGED, UPPER, LOWER, FIRST_UPPER, EVERY_WORD_UPPER
    }

    /**
     * @param useCurrentDate      date/time placeholders read the wall clock instead of each file's
     *                            lastModified
     * @param currentTimeMillis   fixed epoch-millis for {@code useCurrentDate}; null reads the clock
     * @param random              RNG for the random placeholders. A fresh stream per render makes
     *                            the preview re-roll as the user edits, matching TC
     */
    public record Options(
        String nameTemplate,
        String extensionTemplate,
        String search,
        String replace,
        boolean regex,
        boolean caseSensitive,
        CaseMode caseMode,
        CounterConfig counter,
        ZoneId zone,
        boolean useCurrentDate,
        @Nullable Long currentTimeMillis,
        Random random
    ) {
        public static Options defaults() {
            return new Options("[N]", "[E]", "", "", false, true, CaseMode.UNCHANGED,
                CounterConfig.DEFAULT, ZoneId.systemDefault(), false, null, new Random());
        }
    }

    public record Input(String originalName, String parentName, @Nullable FileTime lastModified,
                        String grandParentName) {

        public static Input from(Path path, @Nullable FileTime lastModified) {
            Path parent = path.getParent();
            Path grandParent = parent != null ? parent.getParent() : null;
            return new Input(
                nameOf(path),
                parent != null ? nameOf(parent) : "",
                lastModified,
                grandParent != null ? nameOf(grandParent) : ""
            );
        }

        private static String nameOf(Path path) {
            Path fileName = path.getFileName();
            return fileName != null ? fileName.toString() : "";
        }
    }

    private enum DatePart {
        YEAR, MONTH, DAY, HOUR, MINUTE, SECOND
    }

    private record RenderContext(Input input, String base, String ext, CounterConfig counter, int index,
                                 ZoneId zone, boolean useCurrentDate, @Nullable Long currentTimeMillis,
                                 Random random) {
    }

    private record BaseExt(String base, String ext) {
    }

    private static final String DIGITS = "0123456789";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String MIXED = UPPER + LOWER + DIGITS;

    private MultiRenameTemplate() {
    }

    /**
     * Applies {@code options} to {@code inputs} and returns the generated names in order. The
     * counter advances once per row, not once per {@code [C]} reference — every file consumes a
     * tick even if its template doesn't mention the counter, so a sorted selection lines up
     * row-by-row.
     */
    public static List<String> render(List<Input> inputs, Options options) {
        List<String> result = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            result.add(renderOne(inputs.get(i), options, i));
        }
        return result;
    }

    static String renderOne(Input input, Options options, int index) {
        BaseExt split = splitBaseExt(input.originalName());
        RenderContext ctx = new RenderContext(input, split.base(), split.ext(), options.counter(), index,
            options.zone(), options.useCurrentDate(), options.currentTimeMillis(), options.random());

        String renderedBase = expand(options.nameTemplate(), ctx);
        String renderedExt = expand(options.extensionTemplate(), ctx);
        String joined = renderedExt.isEmpty() ? renderedBase : renderedBase + "." + renderedExt;

        return applyCase(applySearchReplace(joined, options), options.caseMode());
    }

    /**
     * Splits "foo.tar.gz" into ("foo.tar", "gz"). A leading dot stays in the base
     * (".gitignore" -> (".gitignore", "")) because users think of dotfiles as extensionless.
     */
    static BaseExt splitBaseExt(String name) {
        if (name.isEmpty()) {
            return new BaseExt("", "");
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot <= 0) {
            return new BaseExt(name, "");
        }
        return new BaseExt(name.substring(0, lastDot), name.substring(lastDot + 1));
    }

    private static String expand(String template, RenderContext ctx) {
        if (template.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(template.length() + 16);
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c != '[') {
                sb.append(c);
                i++;
                continue;
            }

            int close = template.indexOf(']', i + 1);
            if (close < 0) {
                sb.append(template, i, template.length());
                break;
            }

            String body = template.substring(i + 1, close);
            String expanded = resolvePlaceholder(body, ctx);
            if (expanded == null) {
                // Unknown placeholder — leave it as-is so the user can see the typo.
                sb.append('[').append(body).append(']');
            }
            else {
                sb.append(expanded);
            }
            i = close + 1;
        }
        return sb.toString();
    }

    private static @Nullable String resolvePlaceholder(String body, RenderContext ctx) {
        if (body.isEmpty()) {
            return null;
        }

        // Date tokens are case-sensitive: [M] is month, [n] is minute. The substring/counter tokens
        // (N/E/C/P/G) are case-insensitive for ergonomics — they don't clash.
        char head = body.charAt(0);
        String rest = body.substring(1).trim();

        if (rest.isEmpty()) {
            switch (head) {
                case 'Y':
                    return datePart(DatePart.YEAR, ctx);
                case 'M':
                    return datePart(DatePart.MONTH, ctx);
                case 'D':
                    return datePart(DatePart.DAY, ctx);
                case 'h':
                    return datePart(DatePart.HOUR, ctx);
                case 'n':
                    return datePart(DatePart.MINUTE, ctx);
                case 's':
                    return datePart(DatePart.SECOND, ctx);
                default:
                    break;
            }
        }

        // Random placeholders come before the N/E/P/C/G dispatch because [R…] and
        // [Ra…]/[RA…]/[RM…] share the leading 'R'; the second char decides the route.
        if (Character.toUpperCase(head) == 'R') {
            String random = resolveRandom(body, ctx);
            if (random != null) {
                return random;
            }
        }

        switch (Character.toUpperCase(head)) {
            case 'N':
                return resolveSubstring(ctx.base(), rest);
            case 'E':
                return resolveSubstring(ctx.ext(), rest);
            case 'P':
                return rest.isEmpty() ? ctx.input().parentName() : null;
            case 'G':
                return rest.isEmpty() ? ctx.input().grandParentName() : null;
            case 'C':
                return resolveCounter(rest, ctx.counter(), ctx.index());
            default:
                break;
        }

        if (body.equals("YMD")) {
            return datePart(DatePart.YEAR, ctx) + datePart(DatePart.MONTH, ctx) + datePart(DatePart.DAY, ctx);
        }
        return null;
    }

    /**
     * Substring specs: empty (whole source), {@code "a"}, {@code "a-b"}, {@code "a-"} (open range),
     * {@code "a,n"} (position plus length). Indices are 1-based; negatives count from the end.
     */
    private static String resolveSubstring(String source, String spec) {
        if (spec.isEmpty()) {
            return source;
        }

        int comma = spec.indexOf(',');
        if (comma > 0) {
            Integer a = parseInt(spec.substring(0, comma));
            Integer len = parseInt(spec.substring(comma + 1));
            if (a == null || len == null || len <= 0) {
                return "";
            }
            Integer start = resolveIndex(a, source.length());
            if (start == null) {
                return "";
            }
            return source.substring(start, Math.min(start + len, source.length()));
        }

        int dash = findRangeDash(spec);
        if (dash < 0) {
            Integer idx = parseInt(spec);
            if (idx == null) {
                return "";
            }
            Integer resolved = resolveIndex(idx, source.length());
            return resolved == null ? "" : source.substring(resolved, resolved + 1);
        }

        Integer a = parseInt(spec.substring(0, dash));
        if (a == null) {
            return "";
        }
        Integer start = resolveIndex(a, source.length());
        if (start == null) {
            return "";
        }

        String right = spec.substring(dash + 1);
        if (right.isEmpty()) {
            // Open-ended "a-" means "from a to the end".
            return source.substring(start);
        }

        Integer b = parseInt(right);
        if (b == null) {
            return "";
        }
        Integer end = resolveIndex(b, source.length());
        if (end == null || start > end) {
            return "";
        }
        return source.substring(start, Math.min(end + 1, source.length()));
    }

    /** Finds the range separator in a spec like "2-5" or "-3--1", skipping a leading sign. */
    private static int findRangeDash(String spec) {
        return spec.indexOf('-', spec.startsWith("-") ? 1 : 0);
    }

    /**
     * Turns a 1-based index (positive from the start, negative from the end) into a 0-based offset,
     * or null when out of bounds.
     */
    private static @Nullable Integer resolveIndex(int oneBased, int length) {
        if (length == 0) {
            return null;
        }
        int zero = oneBased > 0 ? oneBased - 1 : length + oneBased;
        return zero >= 0 && zero < length ? zero : null;
    }

    /**
     * Counter specs: {@code ""}, {@code "10"}, {@code "10+2"}, {@code "10+2:3"}. Missing pieces fall
     * back to the config; malformed specs fall back silently.
     */
    private static String resolveCounter(String spec, CounterConfig cfg, int index) {
        int start = cfg.start();
        int step = cfg.step();
        int width = cfg.width();

        if (!spec.isEmpty()) {
            int colon = spec.indexOf(':');
            String head = colon < 0 ? spec : spec.substring(0, colon);
            String tail = colon < 0 ? "" : spec.substring(colon + 1);

            // The step is separated from the start by '+'. The start may be negative, but the step
            // is always a magnitude after a required '+', so a plain indexOf is correct.
            int plus = head.indexOf('+');
            if (plus < 0) {
                Integer parsed = parseInt(head);
                if (parsed != null) {
                    start = parsed;
                }
            }
            else {
                Integer parsedStart = parseInt(head.substring(0, plus));
                if (parsedStart != null) {
                    start = parsedStart;
                }
                Integer parsedStep = parseInt(head.substring(plus + 1));
                if (parsedStep != null) {
                    step = parsedStep;
                }
            }

            if (!tail.isEmpty()) {
                Integer parsedWidth = parseInt(tail);
                if (parsedWidth != null) {
                    width = parsedWidth;
                }
            }
        }

        int value = start + step * index;
        String abs = Integer.toString(Math.abs(value));
        String padded = abs.length() < width ? "0".repeat(width - abs.length()) + abs : abs;
        return value < 0 ? "-" + padded : padded;
    }

    private static String datePart(DatePart kind, RenderContext ctx) {
        long millis;
        if (ctx.useCurrentDate()) {
            millis = ctx.currentTimeMillis() != null ? ctx.currentTimeMillis() : System.currentTimeMillis();
        }
        else {
            FileTime lastModified = ctx.input().lastModified();
            if (lastModified == null) {
                return "";
            }
            millis = lastModified.toMillis();
        }

        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ctx.zone());
        return switch (kind) {
            case YEAR -> String.format("%04d", ldt.getYear());
            case MONTH -> String.format("%02d", ldt.getMonthValue());
            case DAY -> String.format("%02d", ldt.getDayOfMonth());
            case HOUR -> String.format("%02d", ldt.getHour());
            case MINUTE -> String.format("%02d", ldt.getMinute());
            case SECOND -> String.format("%02d", ldt.getSecond());
        };
    }

    /**
     * Random placeholders. Returns null when {@code body} isn't a random spec, so the caller can
     * fall through to the other R-prefixed dispatch.
     */
    private static @Nullable String resolveRandom(String body, RenderContext ctx) {
        if (body.isEmpty() || Character.toUpperCase(body.charAt(0)) != 'R') {
            return null;
        }

        String alphabet;
        int specStart;
        if (body.length() == 1) {
            alphabet = DIGITS;
            specStart = 1;
        }
        else {
            switch (body.charAt(1)) {
                case 'a' -> {
                    alphabet = LOWER;
                    specStart = 2;
                }
                case 'A' -> {
                    alphabet = UPPER;
                    specStart = 2;
                }
                case 'M' -> {
                    alphabet = MIXED;
                    specStart = 2;
                }
                default -> {
                    alphabet = DIGITS;
                    specStart = 1;
                }
            }
        }

        Integer count = parseRandomCount(body.substring(specStart));
        if (count == null) {
            return null;
        }
        if (count <= 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(alphabet.charAt(ctx.random().nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** Accepts a plain integer, a run of {@code #} marks, or empty (a bare {@code [R]} → 1). */
    private static @Nullable Integer parseRandomCount(String spec) {
        if (spec.isEmpty()) {
            return 1;
        }
        boolean allHashes = true;
        for (int i = 0; i < spec.length(); i++) {
            if (spec.charAt(i) != '#') {
                allHashes = false;
                break;
            }
        }
        return allHashes ? spec.length() : parseInt(spec);
    }

    private static String applySearchReplace(String name, Options options) {
        if (options.search().isEmpty()) {
            return name;
        }

        if (options.regex()) {
            int flags = options.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE;
            try {
                return Pattern.compile(options.search(), flags).matcher(name).replaceAll(options.replace());
            }
            catch (PatternSyntaxException e) {
                return name;
            }
        }

        if (options.caseSensitive()) {
            return name.replace(options.search(), options.replace());
        }

        // String.replace is always case-sensitive; a quoted pattern plus CASE_INSENSITIVE keeps the
        // replacement literal, so no $1 back-references leak in when regex mode is off.
        return Pattern.compile(Pattern.quote(options.search()), Pattern.CASE_INSENSITIVE)
            .matcher(name)
            .replaceAll(Matcher.quoteReplacement(options.replace()));
    }

    private static String applyCase(String name, CaseMode mode) {
        switch (mode) {
            case UPPER:
                return name.toUpperCase(Locale.ROOT);
            case LOWER:
                return name.toLowerCase(Locale.ROOT);
            case FIRST_UPPER:
                if (name.isEmpty()) {
                    return name;
                }
                return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1).toLowerCase(Locale.ROOT);
            case EVERY_WORD_UPPER: {
                StringBuilder sb = new StringBuilder(name.length());
                boolean upNext = true;
                for (int i = 0; i < name.length(); i++) {
                    char c = name.charAt(i);
                    if (Character.isLetter(c)) {
                        sb.append(upNext ? Character.toUpperCase(c) : Character.toLowerCase(c));
                        upNext = false;
                    }
                    else {
                        sb.append(c);
                        upNext = true;
                    }
                }
                return sb.toString();
            }
            case UNCHANGED:
            default:
                return name;
        }
    }

    /**
     * Reports name collisions in a proposed rename batch: 0-based indices whose target name is
     * blank, duplicates another target, or already exists on disk while differing from the source.
     *
     * <p>Targets vacated by the batch itself don't count as collisions — renaming a→b and b→c is
     * legal. {@code existsOnDisk} probes the target path resolved against the source's parent.
     */
    public static Set<Integer> detectConflicts(List<Path> sources, List<String> targetNames,
                                               boolean caseInsensitiveDuplicates,
                                               Predicate<Path> existsOnDisk) {
        if (sources.size() != targetNames.size()) {
            throw new IllegalArgumentException("sources/targets length mismatch");
        }

        Set<Integer> conflicts = new HashSet<>();
        Map<String, Integer> seen = new HashMap<>();

        for (int i = 0; i < targetNames.size(); i++) {
            String name = targetNames.get(i);
            if (name.isBlank()) {
                conflicts.add(i);
                continue;
            }
            String key = caseInsensitiveDuplicates ? name.toLowerCase(Locale.ROOT) : name;
            Integer prev = seen.put(key, i);
            if (prev != null) {
                conflicts.add(i);
                conflicts.add(prev);
            }
        }

        Set<String> vacated = new HashSet<>();
        for (int j = 0; j < sources.size(); j++) {
            Path source = sources.get(j);
            Path fileName = source.getFileName();
            if (fileName == null) {
                continue;
            }
            if (!targetNames.get(j).equals(fileName.toString())) {
                vacated.add(pathKey(source, caseInsensitiveDuplicates));
            }
        }

        for (int i = 0; i < sources.size(); i++) {
            if (conflicts.contains(i)) {
                continue;
            }
            Path source = sources.get(i);
            Path fileName = source.getFileName();
            String newName = targetNames.get(i);
            if (fileName != null && newName.equals(fileName.toString())) {
                continue;
            }
            Path target = source.resolveSibling(newName);
            if (vacated.contains(pathKey(target, caseInsensitiveDuplicates))) {
                continue;
            }
            if (existsOnDisk.test(target)) {
                conflicts.add(i);
            }
        }
        return conflicts;
    }

    private static String pathKey(Path path, boolean caseInsensitive) {
        String s = path.toString();
        return caseInsensitive ? s.toLowerCase(Locale.ROOT) : s;
    }

    private static @Nullable Integer parseInt(String value) {
        try {
            return Integer.valueOf(value.trim());
        }
        catch (NumberFormatException e) {
            return null;
        }
    }
}
