package consulo.turtlecommander.settings;

import org.jspecify.annotations.Nullable;

/**
 * Flattened, serializable form of {@link RuleMatcher}. Matcher polymorphism collapses into a
 * discriminator {@code type} plus the union of every variant's fields.
 */
public class SavedColorMatcher {
    public String type = "";

    // Size
    public String sizeOp = "";
    public long sizeBytes = 0L;
    public long sizeBytesMax = 0L;

    // Name / Contains / Text
    public String patternKind = "";
    public String pattern = "";
    public boolean caseSensitive = false;
    public String appliesTo = "";

    // Date
    public String dateField = "";
    public String dateOp = "";
    public long dateEpochMillis = 0L;
    public long dateEpochMillisMax = 0L;
    public long dateAmount = 0L;
    public String dateUnit = "";

    // Text (owner / group / permissions)
    public String textProperty = "";

    // Symlink
    public String symlinkState = "";

    /** Returns null when the discriminator or a required enum value is unrecognised. */
    public @Nullable RuleMatcher toMatcher() {
        try {
            return switch (type) {
                case "SIZE" -> new RuleMatcher.Size(SizeOp.valueOf(sizeOp), sizeBytes, sizeBytesMax);
                case "NAME" -> new RuleMatcher.Name(PatternKind.valueOf(patternKind), pattern, caseSensitive,
                    parseEnum(AppliesTo.class, appliesTo, AppliesTo.BOTH));
                case "CONTAINS" -> new RuleMatcher.Contains(PatternKind.valueOf(patternKind), pattern, caseSensitive);
                case "DATE" -> new RuleMatcher.Date(DateField.valueOf(dateField), DateOp.valueOf(dateOp),
                    dateEpochMillis, dateEpochMillisMax, dateAmount, parseEnum(DateUnit.class, dateUnit, DateUnit.DAYS));
                case "TEXT" -> new RuleMatcher.Text(TextProperty.valueOf(textProperty),
                    PatternKind.valueOf(patternKind), pattern, caseSensitive);
                case "SYMLINK" -> new RuleMatcher.Symlink(parseEnum(SymlinkState.class, symlinkState, SymlinkState.ANY));
                case "NAMED_PIPE" -> RuleMatcher.NamedPipe.INSTANCE;
                default -> null;
            };
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static SavedColorMatcher fromMatcher(RuleMatcher matcher) {
        SavedColorMatcher saved = new SavedColorMatcher();
        switch (matcher) {
            case RuleMatcher.Size size -> {
                saved.type = "SIZE";
                saved.sizeOp = size.op().name();
                saved.sizeBytes = size.bytes();
                saved.sizeBytesMax = size.bytesMax();
            }
            case RuleMatcher.Name name -> {
                saved.type = "NAME";
                saved.patternKind = name.kind().name();
                saved.pattern = name.pattern();
                saved.caseSensitive = name.caseSensitive();
                saved.appliesTo = name.appliesTo().name();
            }
            case RuleMatcher.Contains contains -> {
                saved.type = "CONTAINS";
                saved.patternKind = contains.kind().name();
                saved.pattern = contains.pattern();
                saved.caseSensitive = contains.caseSensitive();
            }
            case RuleMatcher.Date date -> {
                saved.type = "DATE";
                saved.dateField = date.field().name();
                saved.dateOp = date.op().name();
                saved.dateEpochMillis = date.epochMillis();
                saved.dateEpochMillisMax = date.epochMillisMax();
                saved.dateAmount = date.amount();
                saved.dateUnit = date.unit().name();
            }
            case RuleMatcher.Text text -> {
                saved.type = "TEXT";
                saved.textProperty = text.field().name();
                saved.patternKind = text.kind().name();
                saved.pattern = text.pattern();
                saved.caseSensitive = text.caseSensitive();
            }
            case RuleMatcher.Symlink symlink -> {
                saved.type = "SYMLINK";
                saved.symlinkState = symlink.state().name();
            }
            case RuleMatcher.NamedPipe ignored -> saved.type = "NAMED_PIPE";
        }
        return saved;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        }
        catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
