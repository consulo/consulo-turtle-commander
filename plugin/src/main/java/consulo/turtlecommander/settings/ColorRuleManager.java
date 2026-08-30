package consulo.turtlecommander.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * CRUD + seeding for the user's colour-rule list, mirroring {@link ThemeManager}.
 *
 * <p>The built-in rules are seeded once on first use. After that {@code colorRulesInitialized}
 * stays true and the seeder is a no-op even if the user deleted every rule.
 */
public final class ColorRuleManager {
    public enum SaveResult {
        SAVED, REPLACED
    }

    private ColorRuleManager() {
    }

    public static void ensureInitialRules(TurtleCommanderSettings.State state) {
        if (state.colorRulesInitialized) {
            return;
        }
        // builtinRules() already includes the symlink and named-pipe defaults. If the master
        // highlighting switch is off at seed time every rule is written inactive, so the user can
        // enable individual ones later without re-typing them.
        boolean defaultsActive = state.enableFileNameHighlighting;
        for (ColorRule rule : ColorRuleDefaults.builtinRules()) {
            state.colorRules.add(SavedColorRule.fromRule(ColorRuleDefaults.withActive(rule, defaultsActive)));
        }
        state.colorRulesInitialized = true;
    }

    public static List<ColorRule> getAllRules(TurtleCommanderSettings.State state) {
        ensureInitialRules(state);
        List<ColorRule> rules = new ArrayList<>();
        for (SavedColorRule saved : state.colorRules) {
            rules.add(saved.toRule());
        }
        return rules;
    }

    public static List<ColorRule> getActiveRulesForEvaluation(TurtleCommanderSettings.State state) {
        List<ColorRule> active = new ArrayList<>();
        for (ColorRule rule : getAllRules(state)) {
            if (rule.active()) {
                active.add(rule);
            }
        }
        return active;
    }

    public static SaveResult saveRule(TurtleCommanderSettings.State state, ColorRule rule) {
        int index = indexOfRule(state, rule.id());
        if (index >= 0) {
            state.colorRules.set(index, SavedColorRule.fromRule(rule));
            return SaveResult.REPLACED;
        }
        state.colorRules.add(SavedColorRule.fromRule(rule));
        return SaveResult.SAVED;
    }

    public static boolean deleteRule(TurtleCommanderSettings.State state, String id) {
        return state.colorRules.removeIf(saved -> saved.id.equals(id));
    }

    /** Overwrites the ruleset with a fresh copy of the built-in defaults. */
    public static void resetToDefaults(TurtleCommanderSettings.State state) {
        state.colorRules.clear();
        for (ColorRule rule : ColorRuleDefaults.builtinRules()) {
            state.colorRules.add(SavedColorRule.fromRule(rule));
        }
        state.colorRulesInitialized = true;
    }

    public static ColorizationMode getMode(TurtleCommanderSettings.State state) {
        try {
            return ColorizationMode.valueOf(state.colorizationMode);
        }
        catch (IllegalArgumentException e) {
            return ColorizationMode.WINNER;
        }
    }

    public static void setMode(TurtleCommanderSettings.State state, ColorizationMode mode) {
        state.colorizationMode = mode.name();
    }


    private static int indexOfRule(TurtleCommanderSettings.State state, String id) {
        for (int i = 0; i < state.colorRules.size(); i++) {
            if (state.colorRules.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
