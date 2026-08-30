package consulo.turtlecommander.settings;

import java.util.List;

/**
 * A user-named bundle of colour rules, used purely as an exchange format for exporting and
 * importing rule sets across installs. Not persisted in settings.
 */
public record ColorRulesConfig(String name, List<ColorRule> rules) {
    @Override
    public String toString() {
        return name;
    }
}
