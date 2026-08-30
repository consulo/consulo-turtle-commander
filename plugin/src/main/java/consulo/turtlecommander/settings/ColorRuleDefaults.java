package consulo.turtlecommander.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Built-in ruleset for the project-marker directory types that turtle-commander used to colorize
 * via hardcoded logic. Used by the migration seeder so upgraded installs keep their previous look,
 * and by the "Reset to defaults" action.
 */
public final class ColorRuleDefaults {
    private static final String DEFAULT_ID_PREFIX = "default:";

    private ColorRuleDefaults() {
    }

    public static boolean isDefault(ColorRule rule) {
        return rule.id().startsWith(DEFAULT_ID_PREFIX);
    }

    /**
     * Rules detecting common project markers ({@code .idea}, {@code pom.xml}, {@code Cargo.toml}…),
     * plus the symlink and named-pipe defaults. Priorities mirror the original detection
     * fall-through order so WINNER mode reproduces the pre-rule-engine behaviour exactly.
     */
    public static List<ColorRule> builtinRules() {
        List<ColorRule> rules = new ArrayList<>();
        rules.add(containsRule("idea-project", "IntelliJ IDEA project", 100,
            List.of(exact(".idea")), "#5A9AD8", "#5A9AD8"));
        rules.add(containsRule("gradle", "Gradle project", 90,
            List.of(exact("build.gradle.kts"), exact("build.gradle"), exact("settings.gradle.kts"), exact("settings.gradle")),
            "#69B7A2", "#69B7A2"));
        rules.add(containsRule("maven", "Maven project", 80,
            List.of(exact("pom.xml")), "#7A9ABC", "#7A9ABC"));
        rules.add(containsRule("cargo", "Cargo (Rust) project", 70,
            List.of(exact("Cargo.toml")), "#B0A080", "#B0A080"));
        rules.add(containsRule("npm", "Node / npm project", 60,
            List.of(exact("package.json")), "#7AB0A0", "#7AB0A0"));
        rules.add(containsRule("python", "Python project", 50,
            List.of(exact("pyproject.toml"), exact("setup.py"), exact("requirements.txt"), exact(".venv"), exact("venv")),
            "#7AAAC0", "#7AAAC0"));
        rules.add(containsRule("cmake", "CMake project", 40,
            List.of(exact("CMakeLists.txt")), "#7A8AAA", "#7A8AAA"));
        rules.add(containsRule("dotnet", ".NET project", 30,
            List.of(glob("*.csproj"), glob("*.vbproj"), glob("*.fsproj"), exact("Directory.Build.props"), exact("global.json")),
            "#9A8ABB", "#9A8ABB"));
        rules.add(containsRule("git", "Git repository", 20,
            List.of(exact(".git")), "#8AAA7A", "#9AAA7C"));
        rules.addAll(symlinkRules());
        rules.addAll(namedPipeRules());
        return rules;
    }

    /**
     * Gives symbolic links a distinct look out of the box: valid links in cyan (the long-standing
     * terminal convention), broken links in red and struck through. High priority so a link's
     * nature wins over project-marker colouring in WINNER mode.
     */
    public static List<ColorRule> symlinkRules() {
        return List.of(
            symlinkRule("symlink-valid", "Symbolic link", 110, SymlinkState.VALID, "#4DB6AC", false),
            symlinkRule("symlink-broken", "Broken symbolic link", 120, SymlinkState.BROKEN, "#E06666", true)
        );
    }

    /**
     * Gives named pipes (FIFOs) an amber look, following the {@code ls} convention of colouring
     * pipes yellow ({@code pi=33}). Never fires on Windows.
     */
    public static List<ColorRule> namedPipeRules() {
        return List.of(new ColorRule(
            defaultId("named-pipe"),
            "Named pipe (FIFO)",
            115,
            true,
            Combinator.AND,
            List.of(RuleMatcher.NamedPipe.INSTANCE),
            new RuleStyle("#C9A227", "", "#C9A227", null, null, null, "")
        ));
    }

    /** Clones the built-in rules with fresh random IDs — used by the "Reset to defaults" action. */
    public static List<ColorRule> freshBuiltinRules() {
        List<ColorRule> fresh = new ArrayList<>();
        for (ColorRule rule : builtinRules()) {
            fresh.add(withId(rule, UUID.randomUUID().toString()));
        }
        return fresh;
    }

    public static ColorRule withId(ColorRule rule, String id) {
        return new ColorRule(id, rule.name(), rule.priority(), rule.active(), rule.combinator(),
            rule.matchers(), rule.style());
    }

    public static ColorRule withActive(ColorRule rule, boolean active) {
        return new ColorRule(rule.id(), rule.name(), rule.priority(), active, rule.combinator(),
            rule.matchers(), rule.style());
    }

    /** Stable ID used by the migration seeder to detect and refresh defaults. */
    private static String defaultId(String slug) {
        return DEFAULT_ID_PREFIX + slug;
    }

    private static PatternSpec exact(String pattern) {
        return new PatternSpec(PatternKind.EXACT, pattern);
    }

    private static PatternSpec glob(String pattern) {
        return new PatternSpec(PatternKind.GLOB, pattern);
    }

    private static ColorRule containsRule(String slug, String name, int priority, List<PatternSpec> patterns,
                                          String font, String dot) {
        List<RuleMatcher> matchers = new ArrayList<>();
        for (PatternSpec spec : patterns) {
            matchers.add(new RuleMatcher.Contains(spec.kind(), spec.pattern(), false));
        }
        return new ColorRule(
            defaultId(slug),
            name,
            priority,
            true,
            matchers.size() > 1 ? Combinator.OR : Combinator.AND,
            matchers,
            new RuleStyle(font, "", dot, null, null, null, "")
        );
    }

    private static ColorRule symlinkRule(String slug, String name, int priority, SymlinkState state,
                                         String font, boolean strikethrough) {
        return new ColorRule(
            defaultId(slug),
            name,
            priority,
            true,
            Combinator.AND,
            List.of(new RuleMatcher.Symlink(state)),
            new RuleStyle(font, "", font, null, null, strikethrough ? Boolean.TRUE : null, "")
        );
    }

    private record PatternSpec(PatternKind kind, String pattern) {
    }
}
