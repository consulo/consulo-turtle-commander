package consulo.turtlecommander.service;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.settings.ColorRule;
import consulo.turtlecommander.settings.ColorRuleEngine;
import consulo.turtlecommander.settings.ColorizationMode;
import consulo.turtlecommander.settings.ContainsEvaluator;
import consulo.turtlecommander.settings.ResolvedStyle;
import consulo.turtlecommander.settings.RuleMatcher;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * Platform-independent half of colorization resolution. Owns the child-name cache used by
 * {@code Contains} matchers and delegates rule matching to {@link ColorRuleEngine}. Tests drive
 * this class directly, so it never touches application services.
 */
public class ColorizationResolver {
    static final int CACHE_MAX_SIZE = 500;
    static final long CACHE_TTL_MS = 10L * 60 * 1000;

    /**
     * Upper bound on how many children are read for Contains matching. Markers like {@code .git} or
     * {@code pom.xml} live near the top, and bounding avoids eating memory on pathological
     * directories.
     */
    static final int MAX_CHILDREN_SCANNED = 10_000;

    private record CachedChildren(List<String> names, long timestamp) {
    }

    private final Map<Path, CachedChildren> myChildCache =
        new LinkedHashMap<>(CACHE_MAX_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Path, CachedChildren> eldest) {
                return size() > CACHE_MAX_SIZE;
            }
        };

    /** Test-only clock override. */
    private LongSupplier myClock = System::currentTimeMillis;

    /** Test-only directory-listing override. Returns null when the path can't be listed. */
    private Function<Path, @Nullable List<String>> myReadChildNames = ColorizationResolver::listChildNames;

    public ResolvedStyle resolve(FileEntry entry, List<ColorRule> rules, ColorizationMode mode, boolean enabled) {
        return resolve(entry, rules, mode, enabled, hasContainsMatcher(rules));
    }

    public ResolvedStyle resolve(FileEntry entry, List<ColorRule> rules, ColorizationMode mode, boolean enabled,
                                 boolean anyContainsMatcher) {
        if (!enabled || rules.isEmpty() || entry.parentLink()) {
            return ResolvedStyle.EMPTY;
        }
        ContainsEvaluator evaluator = anyContainsMatcher && entry.directory()
            ? cachingEvaluator()
            : ContainsEvaluator.EMPTY;
        return ColorRuleEngine.resolve(rules, entry, mode, evaluator);
    }

    public void invalidate(Path directory) {
        synchronized (myChildCache) {
            myChildCache.remove(directory);
        }
    }

    public void clearCache() {
        synchronized (myChildCache) {
            myChildCache.clear();
        }
    }

    public static boolean hasContainsMatcher(List<ColorRule> rules) {
        for (ColorRule rule : rules) {
            for (RuleMatcher matcher : rule.matchers()) {
                if (matcher instanceof RuleMatcher.Contains) {
                    return true;
                }
            }
        }
        return false;
    }

    int cachedChildrenSize() {
        synchronized (myChildCache) {
            return myChildCache.size();
        }
    }

    boolean isCached(Path directory) {
        synchronized (myChildCache) {
            CachedChildren cached = myChildCache.get(directory);
            return cached != null && myClock.getAsLong() - cached.timestamp() <= CACHE_TTL_MS;
        }
    }

    void setClockForTesting(LongSupplier clock) {
        myClock = clock;
    }

    void setReadChildNamesForTesting(Function<Path, @Nullable List<String>> readChildNames) {
        myReadChildNames = readChildNames;
    }

    private ContainsEvaluator cachingEvaluator() {
        return entry -> {
            if (!entry.directory()) {
                return null;
            }
            Path path = entry.path();

            synchronized (myChildCache) {
                CachedChildren cached = myChildCache.get(path);
                if (cached != null && myClock.getAsLong() - cached.timestamp() <= CACHE_TTL_MS) {
                    return cached.names();
                }
            }

            List<String> names = myReadChildNames.apply(path);
            if (names == null) {
                return null;
            }

            synchronized (myChildCache) {
                myChildCache.put(path, new CachedChildren(names, myClock.getAsLong()));
            }
            return names;
        };
    }

    private static @Nullable List<String> listChildNames(Path path) {
        try (Stream<Path> stream = Files.list(path)) {
            return stream.limit(MAX_CHILDREN_SCANNED)
                .map(child -> child.getFileName().toString())
                .toList();
        }
        catch (Exception e) {
            return null;
        }
    }
}
