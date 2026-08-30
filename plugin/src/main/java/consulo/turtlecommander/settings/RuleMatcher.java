package consulo.turtlecommander.settings;

import consulo.turtlecommander.model.FileEntry;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * One condition inside a {@link ColorRule}.
 *
 * <p>{@link #cost()} is a relative hint the engine uses to reorder matchers cheapest-first, so
 * short-circuiting AND/OR skips expensive work when possible.
 */
public sealed interface RuleMatcher {
    int cost();

    boolean matches(FileEntry entry, ContainsEvaluator contains);

    /** Matches file size. Directories never match. */
    record Size(SizeOp op, long bytes, long bytesMax) implements RuleMatcher {
        @Override
        public int cost() {
            return 0;
        }

        @Override
        public boolean matches(FileEntry entry, ContainsEvaluator contains) {
            if (entry.directory()) {
                return false;
            }
            long s = entry.size();
            return switch (op) {
                case LT -> s < bytes;
                case LE -> s <= bytes;
                case EQ -> s == bytes;
                case GE -> s >= bytes;
                case GT -> s > bytes;
                case BETWEEN -> s >= bytes && s <= bytesMax;
            };
        }
    }

    /** Matches the entry name, optionally restricted to files or directories. */
    final class Name implements RuleMatcher {
        private final PatternKind myKind;
        private final String myPattern;
        private final boolean myCaseSensitive;
        private final AppliesTo myAppliesTo;
        private final @Nullable Predicate<String> myCompiled;

        public Name(PatternKind kind, String pattern, boolean caseSensitive, AppliesTo appliesTo) {
            myKind = kind;
            myPattern = pattern;
            myCaseSensitive = caseSensitive;
            myAppliesTo = appliesTo;
            myCompiled = NamePatterns.compile(kind, pattern, caseSensitive);
        }

        public PatternKind kind() {
            return myKind;
        }

        public String pattern() {
            return myPattern;
        }

        public boolean caseSensitive() {
            return myCaseSensitive;
        }

        public AppliesTo appliesTo() {
            return myAppliesTo;
        }

        @Override
        public int cost() {
            return 1;
        }

        @Override
        public boolean matches(FileEntry entry, ContainsEvaluator contains) {
            if (entry.parentLink() || myCompiled == null) {
                return false;
            }
            if (myAppliesTo == AppliesTo.FILE && entry.directory()) {
                return false;
            }
            if (myAppliesTo == AppliesTo.DIR && !entry.directory()) {
                return false;
            }
            return myCompiled.test(entry.name());
        }
    }

    /** Matches a directory that contains at least one child whose name matches the pattern. */
    final class Contains implements RuleMatcher {
        private final PatternKind myKind;
        private final String myPattern;
        private final boolean myCaseSensitive;
        private final @Nullable Predicate<String> myCompiled;

        public Contains(PatternKind kind, String pattern, boolean caseSensitive) {
            myKind = kind;
            myPattern = pattern;
            myCaseSensitive = caseSensitive;
            myCompiled = NamePatterns.compile(kind, pattern, caseSensitive);
        }

        public PatternKind kind() {
            return myKind;
        }

        public String pattern() {
            return myPattern;
        }

        public boolean caseSensitive() {
            return myCaseSensitive;
        }

        @Override
        public int cost() {
            return 2;
        }

        @Override
        public boolean matches(FileEntry entry, ContainsEvaluator contains) {
            if (!entry.directory() || myCompiled == null) {
                return false;
            }
            List<String> children = contains.listChildNames(entry);
            if (children == null) {
                return false;
            }
            for (String child : children) {
                if (myCompiled.test(child)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Matches the creation or last-modified timestamp. Absolute operators compare against
     * {@code epochMillis}/{@code epochMillisMax}; relative ones compare against
     * {@code now - amount * unit}, evaluated at match time. Entries with no timestamp never match.
     */
    record Date(DateField field, DateOp op, long epochMillis, long epochMillisMax, long amount, DateUnit unit)
        implements RuleMatcher {

        @Override
        public int cost() {
            return 0;
        }

        @Override
        public boolean matches(FileEntry entry, ContainsEvaluator contains) {
            if (entry.parentLink()) {
                return false;
            }
            java.nio.file.attribute.FileTime fileTime =
                field == DateField.CREATED ? entry.creationTime() : entry.lastModified();
            if (fileTime == null) {
                return false;
            }
            long t = fileTime.toMillis();
            return switch (op) {
                case BEFORE -> t < epochMillis;
                case AFTER -> t >= epochMillis;
                case BETWEEN -> t >= epochMillis && t <= epochMillisMax;
                case WITHIN_LAST -> amount > 0L && t >= System.currentTimeMillis() - unit.toMillis(amount);
                case OLDER_THAN -> amount > 0L && t < System.currentTimeMillis() - unit.toMillis(amount);
            };
        }
    }

    /**
     * Matches a string-valued attribute — owner, group or permissions — using the same
     * EXACT/GLOB/REGEX matching as {@link Name}. On Windows the permissions string is DOS
     * attribute letters (e.g. {@code RHSA}) and group is usually empty.
     */
    final class Text implements RuleMatcher {
        private final TextProperty myField;
        private final PatternKind myKind;
        private final String myPattern;
        private final boolean myCaseSensitive;
        private final @Nullable Predicate<String> myCompiled;

        public Text(TextProperty field, PatternKind kind, String pattern, boolean caseSensitive) {
            myField = field;
            myKind = kind;
            myPattern = pattern;
            myCaseSensitive = caseSensitive;
            myCompiled = NamePatterns.compile(kind, pattern, caseSensitive);
        }

        public TextProperty field() {
            return myField;
        }

        public PatternKind kind() {
            return myKind;
        }

        public String pattern() {
            return myPattern;
        }

        public boolean caseSensitive() {
            return myCaseSensitive;
        }

        @Override
        public int cost() {
            return 1;
        }

        @Override
        public boolean matches(FileEntry entry, ContainsEvaluator contains) {
            if (entry.parentLink() || myCompiled == null) {
                return false;
            }
            String value = switch (myField) {
                case OWNER -> entry.owner();
                case GROUP -> entry.group();
                case PERMISSIONS -> entry.permissions();
            };
            return myCompiled.test(value);
        }
    }

    /** Matches symbolic links; non-symlink entries never match. */
    record Symlink(SymlinkState state) implements RuleMatcher {
        @Override
        public int cost() {
            return 0;
        }

        @Override
        public boolean matches(FileEntry entry, ContainsEvaluator contains) {
            if (!entry.symbolicLink()) {
                return false;
            }
            return switch (state) {
                case ANY -> true;
                case VALID -> !entry.brokenSymlink();
                case BROKEN -> entry.brokenSymlink();
            };
        }
    }

    /**
     * Matches Unix named pipes (FIFOs). Carries no state — an entry either is a pipe or isn't —
     * and is always false on Windows.
     */
    record NamedPipe() implements RuleMatcher {
        public static final NamedPipe INSTANCE = new NamedPipe();

        @Override
        public int cost() {
            return 0;
        }

        @Override
        public boolean matches(FileEntry entry, ContainsEvaluator contains) {
            return entry.namedPipe();
        }
    }
}
