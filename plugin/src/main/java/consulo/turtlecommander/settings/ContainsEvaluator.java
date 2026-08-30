package consulo.turtlecommander.settings;

import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Looks up the top-level entries of a directory. Abstracted so the engine can be exercised
 * without a real filesystem and so callers can cache results. Returns null if the entry is not a
 * directory or cannot be listed.
 */
@FunctionalInterface
public interface ContainsEvaluator {
    ContainsEvaluator EMPTY = entry -> null;

    @Nullable
    List<String> listChildNames(FileEntry entry);
}
