package consulo.turtlecommander.settings;

import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.ui.Alerts;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.IntBox;
import consulo.ui.Label;
import consulo.ui.Space;
import consulo.ui.StaticPosition;
import consulo.ui.TextAttribute;
import consulo.ui.TextBox;
import consulo.ui.WidthAndHeight;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.DialogValue;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.TableLayout;
import consulo.ui.layout.VerticalLayout;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Edits one {@link RuleMatcher}. The kind selector shows one form and hides the others; OK reads the visible form
 * into the matching variant. Created and Modified share a date form; Owner, Group and Permissions share a text form.
 */
public final class ColorMatcherEditDialogDescriptor extends DialogDescriptor {
    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB"};
    private static final long KB = 1024L;
    private static final long MB = 1024L * 1024;
    private static final long GB = 1024L * 1024 * 1024;

    /** What the user is matching on. Several kinds map onto one form. */
    public enum Kind {
        SIZE("Size", "SIZE"),
        NAME("Name", "NAME"),
        CREATED("Created date", "DATE"),
        MODIFIED("Modified date", "DATE"),
        OWNER("Owner / user", "TEXT"),
        GROUP("Group", "TEXT"),
        PERMISSIONS("Permissions", "TEXT"),
        CONTAINS("Directory contains", "CONTAINS"),
        SYMLINK("Symbolic link", "SYMLINK"),
        NAMED_PIPE("Named pipe (FIFO)", "NAMED_PIPE");

        private final String myLabel;
        private final String myCardName;

        Kind(String label, String cardName) {
            myLabel = label;
            myCardName = cardName;
        }

        public String getLabel() {
            return myLabel;
        }

        String cardName() {
            return myCardName;
        }

        static Kind from(RuleMatcher matcher) {
            return switch (matcher) {
                case RuleMatcher.Size ignored -> SIZE;
                case RuleMatcher.Name ignored -> NAME;
                case RuleMatcher.Contains ignored -> CONTAINS;
                case RuleMatcher.Symlink ignored -> SYMLINK;
                case RuleMatcher.NamedPipe ignored -> NAMED_PIPE;
                case RuleMatcher.Date date -> date.field() == DateField.CREATED ? CREATED : MODIFIED;
                case RuleMatcher.Text text -> switch (text.field()) {
                    case OWNER -> OWNER;
                    case GROUP -> GROUP;
                    case PERMISSIONS -> PERMISSIONS;
                };
            };
        }
    }

    /** Every input of the dialog; made in {@link #createCenterComponent}, on the UI thread of the dialog. */
    private static final class Form {
        final ComboBox<Kind> kind = labelled(ComboBox.create(Kind.values()), Kind::getLabel);

        final ComboBox<SizeOp> sizeOp = labelled(ComboBox.create(SizeOp.values()), SizeOp::getLabel);
        final IntBox sizeValue = IntBox.create(1).withRange(0, Integer.MAX_VALUE);
        final ComboBox<String> sizeUnit = ComboBox.create(SIZE_UNITS);
        final Label sizeMaxLabel = Label.create(LocalizeValue.localizeTODO("And ≤:"));
        final IntBox sizeMax = IntBox.create(1).withRange(0, Integer.MAX_VALUE);
        final ComboBox<String> sizeMaxUnit = ComboBox.create(SIZE_UNITS);
        final HorizontalLayout sizeMaxRow = row(sizeMax, sizeMaxUnit);

        final ComboBox<PatternKind> nameKind = labelled(ComboBox.create(PatternKind.values()), PatternKind::getLabel);
        final TextBox namePattern = TextBox.create("");
        final CheckBox nameCase = CheckBox.create(LocalizeValue.localizeTODO("Case sensitive"), false);
        final ComboBox<AppliesTo> nameApplies = labelled(ComboBox.create(AppliesTo.values()), AppliesTo::getLabel);

        final ComboBox<PatternKind> containsKind = labelled(ComboBox.create(PatternKind.values()), PatternKind::getLabel);
        final TextBox containsPattern = TextBox.create("");
        final CheckBox containsCase = CheckBox.create(LocalizeValue.localizeTODO("Case sensitive"), false);

        final ComboBox<DateOp> dateOp = labelled(ComboBox.create(DateOp.values()), DateOp::getLabel);
        final Label dateValueLabel = Label.create(LocalizeValue.localizeTODO("Date:"));
        final TextBox dateValue = TextBox.create("").withVisibleLength(12);
        final Label dateMaxLabel = Label.create(LocalizeValue.localizeTODO("And through:"));
        final TextBox dateMax = TextBox.create("").withVisibleLength(12);
        final Label dateAmountLabel = Label.create(LocalizeValue.localizeTODO("Amount:"));
        final IntBox dateAmount = IntBox.create(7).withRange(1, Integer.MAX_VALUE);
        final ComboBox<DateUnit> dateUnit = labelled(ComboBox.create(DateUnit.values()), DateUnit::getLabel);
        final HorizontalLayout dateAmountRow = row(dateAmount, dateUnit);

        final ComboBox<PatternKind> textKind = labelled(ComboBox.create(PatternKind.values()), PatternKind::getLabel);
        final TextBox textPattern = TextBox.create("");
        final CheckBox textCase = CheckBox.create(LocalizeValue.localizeTODO("Case sensitive"), false);

        final ComboBox<SymlinkState> symlinkState =
            labelled(ComboBox.create(SymlinkState.values()), SymlinkState::getLabel);

        /** The form per card name; exactly one is visible. */
        final Map<String, Component> cards = new LinkedHashMap<>();

        @RequiredUIAccess
        Form() {
            // the first item would otherwise be shown until a matcher is loaded
            sizeOp.setValueByIndex(0);
            sizeUnit.setValue("B");
            sizeMaxUnit.setValue("B");
            nameKind.setValueByIndex(0);
            nameApplies.setValueByIndex(0);
            containsKind.setValueByIndex(0);
            dateOp.setValueByIndex(0);
            dateUnit.setValueByIndex(0);
            textKind.setValueByIndex(0);
            symlinkState.setValueByIndex(0);
        }
    }

    private final @Nullable RuleMatcher myInitial;

    private @Nullable Form myForm;
    private @Nullable RuleMatcher myResult;

    public ColorMatcherEditDialogDescriptor(@Nullable RuleMatcher initial) {
        super(LocalizeValue.localizeTODO(initial == null ? "Add matcher" : "Edit matcher"));
        myInitial = initial;
    }

    /** Populated on OK; null when the dialog was cancelled. */
    public @Nullable RuleMatcher getResult() {
        return myResult;
    }

    private static <T> ComboBox<T> labelled(ComboBox<T> combo, Function<T, String> label) {
        combo.setTextRenderer(value -> LocalizeValue.of(value != null ? label.apply(value) : ""));
        return combo;
    }

    @RequiredUIAccess
    private static HorizontalLayout row(Component first, Component second) {
        HorizontalLayout row = HorizontalLayout.create(Space.SMALL);
        row.add(first);
        row.add(second);
        return row;
    }

    // --- Layout -------------------------------------------------------------------------------------

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        Form form = new Form();
        myForm = form;

        form.cards.put("SIZE", buildSizeCard(form));
        form.cards.put("NAME", buildNameCard(form));
        form.cards.put("DATE", buildDateCard(form));
        form.cards.put("TEXT", buildTextCard(form));
        form.cards.put("CONTAINS", buildContainsCard(form));
        form.cards.put("SYMLINK", buildSymlinkCard(form));
        form.cards.put("NAMED_PIPE", buildNamedPipeCard());

        VerticalLayout cardStack = VerticalLayout.create();
        for (Component card : form.cards.values()) {
            cardStack.add(card);
        }

        loadFrom(form, myInitial);
        form.kind.addValueListener(event -> showSelectedCard(form));
        form.sizeOp.addValueListener(event -> updateSizeVisibility(form));
        form.dateOp.addValueListener(event -> updateDateVisibility(form));
        showSelectedCard(form);

        DockLayout top = DockLayout.create(Space.SMALL);
        top.left(Label.create(LocalizeValue.localizeTODO("Match by:")));
        top.center(form.kind);

        return DockLayout.create(Space.MEDIUM).top(top).center(cardStack);
    }

    /** A titled form of label / field rows; a null label leaves its cell empty. */
    @RequiredUIAccess
    private static Component card(String title, @Nullable Component[][] rows) {
        TableLayout table = TableLayout.create(StaticPosition.TOP);
        for (int i = 0; i < rows.length; i++) {
            Component label = rows[i][0];
            if (label != null) {
                table.add(label, TableLayout.cell(i, 0));
            }
            Component field = rows[i][1];
            if (field != null) {
                // text fields stretch over the row, as they did in the Swing form
                table.add(field, field instanceof TextBox ? TableLayout.cell(i, 1).fill() : TableLayout.cell(i, 1));
            }
        }
        return LabeledLayout.create(LocalizeValue.localizeTODO(title), table);
    }

    @RequiredUIAccess
    private static Label text(String text) {
        return Label.create(LocalizeValue.localizeTODO(text));
    }

    @RequiredUIAccess
    private static Label hint(String text) {
        Label label = Label.create(LocalizeValue.localizeTODO(text));
        label.setForegroundColor(TextAttribute.GRAY.getForegroundColor());
        return label;
    }

    @RequiredUIAccess
    private static Component buildSizeCard(Form form) {
        return card("Size", new Component[][]{
            {text("Operator:"), form.sizeOp},
            {text("Value:"), row(form.sizeValue, form.sizeUnit)},
            {form.sizeMaxLabel, form.sizeMaxRow},
        });
    }

    @RequiredUIAccess
    private static Component buildNameCard(Form form) {
        return card("Name", new Component[][]{
            {text("Pattern kind:"), form.nameKind},
            {text("Pattern:"), form.namePattern},
            {text("Applies to:"), form.nameApplies},
            {null, form.nameCase},
        });
    }

    @RequiredUIAccess
    private static Component buildContainsCard(Form form) {
        return card("Directory contains", new Component[][]{
            {text("Pattern kind:"), form.containsKind},
            {text("Pattern:"), form.containsPattern},
            {null, form.containsCase},
        });
    }

    @RequiredUIAccess
    private static Component buildDateCard(Form form) {
        return card("Date", new Component[][]{
            {text("Operator:"), form.dateOp},
            {form.dateValueLabel, form.dateValue},
            {form.dateMaxLabel, form.dateMax},
            {form.dateAmountLabel, form.dateAmountRow},
            {null, hint("Dates are YYYY-MM-DD")},
        });
    }

    @RequiredUIAccess
    private static Component buildTextCard(Form form) {
        return card("Value", new Component[][]{
            {text("Pattern kind:"), form.textKind},
            {text("Pattern:"), form.textPattern},
            {null, form.textCase},
            {null, hint("e.g. perms glob *w* or owner = root")},
        });
    }

    @RequiredUIAccess
    private static Component buildSymlinkCard(Form form) {
        return card("Symbolic link", new Component[][]{
            {text("State:"), form.symlinkState},
            {null, hint("Matches symlinks; \"broken\" = target missing")},
        });
    }

    @RequiredUIAccess
    private static Component buildNamedPipeCard() {
        // no fields: an entry either is a FIFO or it isn't, so explain the Unix-only scope instead
        VerticalLayout layout = VerticalLayout.create(Space.SMALL);
        layout.add(hint("Matches Unix named pipes (FIFOs). No options."));
        layout.add(hint("Never matches on Windows."));
        return LabeledLayout.create(LocalizeValue.localizeTODO("Named pipe"), layout);
    }

    // --- Card and field visibility --------------------------------------------------------------------

    @RequiredUIAccess
    private static void showSelectedCard(Form form) {
        String visible = selectedKind(form).cardName();
        for (Map.Entry<String, Component> card : form.cards.entrySet()) {
            card.getValue().setVisible(card.getKey().equals(visible));
        }
        updateSizeVisibility(form);
        updateDateVisibility(form);
    }

    @RequiredUIAccess
    private static void updateSizeVisibility(Form form) {
        boolean between = form.sizeOp.getValue() == SizeOp.BETWEEN;
        form.sizeMaxLabel.setVisible(between);
        form.sizeMaxRow.setVisible(between);
    }

    /** Absolute operators need dates; the relative ones need an amount and a unit. */
    @RequiredUIAccess
    private static void updateDateVisibility(Form form) {
        DateOp op = form.dateOp.getValue();
        if (op == null) {
            return;
        }
        boolean absolute = op == DateOp.BEFORE || op == DateOp.AFTER || op == DateOp.BETWEEN;
        boolean between = op == DateOp.BETWEEN;
        boolean relative = op == DateOp.WITHIN_LAST || op == DateOp.OLDER_THAN;

        form.dateValueLabel.setVisible(absolute);
        form.dateValue.setVisible(absolute);
        form.dateMaxLabel.setVisible(between);
        form.dateMax.setVisible(between);
        form.dateAmountLabel.setVisible(relative);
        form.dateAmountRow.setVisible(relative);
    }

    @RequiredUIAccess
    @Override
    public @Nullable Component getPreferredFocusedComponent() {
        Form form = myForm;
        if (form == null) {
            return super.getPreferredFocusedComponent();
        }
        // focus the value the user came to type, not the kind selector they already set
        return switch (selectedKind(form)) {
            case SIZE -> form.sizeValue;
            case NAME -> form.namePattern;
            case CONTAINS -> form.containsPattern;
            case OWNER, GROUP, PERMISSIONS -> form.textPattern;
            case CREATED, MODIFIED -> form.dateValue.isVisible() ? form.dateValue
                : form.dateAmount.isVisible() ? form.dateAmount : form.dateOp;
            case SYMLINK -> form.symlinkState;
            case NAMED_PIPE -> form.kind;
        };
    }

    @Override
    public @Nullable WidthAndHeight getInitialSize() {
        // in font sizes: the kind row plus the tallest form, the date one with five rows
        return WidthAndHeight.ofFont(36, 22);
    }

    // --- Loading and saving ------------------------------------------------------------------------------

    private static Kind selectedKind(Form form) {
        Kind kind = form.kind.getValue();
        return kind != null ? kind : Kind.CONTAINS;
    }

    @RequiredUIAccess
    private static void loadFrom(Form form, @Nullable RuleMatcher matcher) {
        if (matcher == null) {
            loadDefaults(form);
            return;
        }
        form.kind.setValue(Kind.from(matcher));

        switch (matcher) {
            case RuleMatcher.Size size -> {
                form.sizeOp.setValue(size.op());
                applySplitSize(size.bytes(), form.sizeValue, form.sizeUnit);
                applySplitSize(size.bytesMax(), form.sizeMax, form.sizeMaxUnit);
            }
            case RuleMatcher.Name name -> {
                form.nameKind.setValue(name.kind());
                form.namePattern.setValue(name.pattern());
                form.nameCase.setValue(name.caseSensitive());
                form.nameApplies.setValue(name.appliesTo());
            }
            case RuleMatcher.Contains contains -> {
                form.containsKind.setValue(contains.kind());
                form.containsPattern.setValue(contains.pattern());
                form.containsCase.setValue(contains.caseSensitive());
            }
            case RuleMatcher.Date date -> {
                form.dateOp.setValue(date.op());
                form.dateValue.setValue(date.epochMillis() != 0L ? isoDate(date.epochMillis()) : "");
                form.dateMax.setValue(date.epochMillisMax() != 0L ? isoDate(date.epochMillisMax()) : "");
                form.dateAmount.setValue(date.amount() > 0L ? clampToInt(date.amount()) : 7);
                form.dateUnit.setValue(date.unit());
            }
            case RuleMatcher.Text text -> {
                form.textKind.setValue(text.kind());
                form.textPattern.setValue(text.pattern());
                form.textCase.setValue(text.caseSensitive());
            }
            case RuleMatcher.Symlink symlink -> form.symlinkState.setValue(symlink.state());
            case RuleMatcher.NamedPipe ignored -> {
                // nothing to load: the matcher carries no options
            }
        }
    }

    /** Sensible starting points for a brand-new matcher, so no form opens empty. */
    @RequiredUIAccess
    private static void loadDefaults(Form form) {
        form.kind.setValue(Kind.CONTAINS);
        form.containsKind.setValue(PatternKind.EXACT);
        form.sizeUnit.setValue("MB");
        form.sizeMaxUnit.setValue("MB");
        form.nameApplies.setValue(AppliesTo.BOTH);
        form.dateOp.setValue(DateOp.WITHIN_LAST);
        form.dateUnit.setValue(DateUnit.DAYS);
        form.textKind.setValue(PatternKind.EXACT);
    }

    /** Shows a byte count in the largest unit that divides it exactly, as the user entered it. */
    @RequiredUIAccess
    private static void applySplitSize(long bytes, IntBox valueBox, ComboBox<String> unitCombo) {
        if (bytes != 0L && bytes % GB == 0) {
            valueBox.setValue(clampToInt(bytes / GB));
            unitCombo.setValue("GB");
        }
        else if (bytes != 0L && bytes % MB == 0) {
            valueBox.setValue(clampToInt(bytes / MB));
            unitCombo.setValue("MB");
        }
        else if (bytes != 0L && bytes % KB == 0) {
            valueBox.setValue(clampToInt(bytes / KB));
            unitCombo.setValue("KB");
        }
        else {
            valueBox.setValue(clampToInt(bytes));
            unitCombo.setValue("B");
        }
    }

    /** The unified number box holds an int; a larger stored value (only reachable by hand-editing) is capped. */
    private static int clampToInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    private static long toBytes(long value, @Nullable String unit) {
        return switch (String.valueOf(unit)) {
            case "KB" -> value * KB;
            case "MB" -> value * MB;
            case "GB" -> value * GB;
            default -> value;
        };
    }

    /** An invalid form keeps the dialog open, with the reason. */
    @RequiredUIAccess
    @Override
    public boolean canHandle(AnAction action, @Nullable DialogValue value) {
        Form form = myForm;
        if (!(action instanceof DialogOkAction) || form == null) {
            return true;
        }
        String error = validate(form);
        if (error != null) {
            Alerts.okError(LocalizeValue.of(error)).showAsync();
            return false;
        }
        myResult = buildMatcher(form);
        return true;
    }

    private static RuleMatcher buildMatcher(Form form) {
        return switch (selectedKind(form)) {
            case SIZE -> buildSizeMatcher(form);
            case NAME -> new RuleMatcher.Name(selected(form.nameKind, PatternKind.EXACT),
                textOf(form.namePattern), checked(form.nameCase), selected(form.nameApplies, AppliesTo.BOTH));
            case CONTAINS -> new RuleMatcher.Contains(selected(form.containsKind, PatternKind.EXACT),
                textOf(form.containsPattern), checked(form.containsCase));
            case CREATED -> buildDateMatcher(form, DateField.CREATED);
            case MODIFIED -> buildDateMatcher(form, DateField.MODIFIED);
            case OWNER -> buildTextMatcher(form, TextProperty.OWNER);
            case GROUP -> buildTextMatcher(form, TextProperty.GROUP);
            case PERMISSIONS -> buildTextMatcher(form, TextProperty.PERMISSIONS);
            case SYMLINK -> new RuleMatcher.Symlink(selected(form.symlinkState, SymlinkState.values()[0]));
            case NAMED_PIPE -> new RuleMatcher.NamedPipe();
        };
    }

    private static RuleMatcher.Size buildSizeMatcher(Form form) {
        SizeOp op = selected(form.sizeOp, SizeOp.values()[0]);
        long bytes = toBytes(number(form.sizeValue, 0), form.sizeUnit.getValue());
        long bytesMax = op == SizeOp.BETWEEN ? toBytes(number(form.sizeMax, 0), form.sizeMaxUnit.getValue()) : 0L;
        return new RuleMatcher.Size(op, bytes, bytesMax);
    }

    /** Reads the date form; {@link #validate} has already vetted the fields. */
    private static RuleMatcher.Date buildDateMatcher(Form form, DateField field) {
        DateOp op = selected(form.dateOp, DateOp.WITHIN_LAST);
        return switch (op) {
            case BEFORE, AFTER -> new RuleMatcher.Date(field, op,
                startOfDayMillis(LocalDate.parse(textOf(form.dateValue))), 0L, 0L, DateUnit.DAYS);
            case BETWEEN -> new RuleMatcher.Date(field, op,
                startOfDayMillis(LocalDate.parse(textOf(form.dateValue))),
                endOfDayMillis(LocalDate.parse(textOf(form.dateMax))), 0L, DateUnit.DAYS);
            case WITHIN_LAST, OLDER_THAN -> new RuleMatcher.Date(field, op, 0L, 0L,
                number(form.dateAmount, 0), selected(form.dateUnit, DateUnit.DAYS));
        };
    }

    private static RuleMatcher.Text buildTextMatcher(Form form, TextProperty field) {
        return new RuleMatcher.Text(field, selected(form.textKind, PatternKind.EXACT), textOf(form.textPattern),
            checked(form.textCase));
    }

    // --- Validation -------------------------------------------------------------------------------------

    /** @return the reason the form can't be saved, or null when it can */
    private static @Nullable String validate(Form form) {
        return switch (selectedKind(form)) {
            case NAME -> validatePattern(selected(form.nameKind, PatternKind.EXACT), textOf(form.namePattern));
            case CONTAINS -> validatePattern(selected(form.containsKind, PatternKind.EXACT), textOf(form.containsPattern));
            case OWNER, GROUP, PERMISSIONS -> validatePattern(selected(form.textKind, PatternKind.EXACT),
                textOf(form.textPattern));
            case CREATED, MODIFIED -> validateDateCard(form);
            case SIZE -> form.sizeValue.getValue() == null
                || (form.sizeOp.getValue() == SizeOp.BETWEEN && form.sizeMax.getValue() == null)
                ? "Enter a size" : null;
            case SYMLINK, NAMED_PIPE -> null;
        };
    }

    /** A pattern must be non-empty and compile, so a broken rule can't be saved and silently never match. */
    private static @Nullable String validatePattern(PatternKind kind, String pattern) {
        if (pattern.isEmpty()) {
            return "Pattern cannot be empty";
        }
        if (kind == PatternKind.EXACT) {
            return null;
        }
        try {
            Pattern.compile(kind == PatternKind.GLOB ? NamePatterns.globToRegex(pattern) : pattern);
            return null;
        }
        catch (PatternSyntaxException e) {
            return kind == PatternKind.GLOB ? "Invalid glob pattern" : "Invalid regular expression: " + e.getDescription();
        }
    }

    private static @Nullable String validateDateCard(Form form) {
        DateOp op = selected(form.dateOp, DateOp.WITHIN_LAST);
        return switch (op) {
            case BEFORE, AFTER -> validateIsoDate(textOf(form.dateValue));
            case BETWEEN -> validateDateRange(form);
            case WITHIN_LAST, OLDER_THAN -> number(form.dateAmount, 0) <= 0L ? "Amount must be positive" : null;
        };
    }

    private static @Nullable String validateDateRange(Form form) {
        String start = validateIsoDate(textOf(form.dateValue));
        if (start != null) {
            return start;
        }
        String end = validateIsoDate(textOf(form.dateMax));
        if (end != null) {
            return end;
        }
        LocalDate low = LocalDate.parse(textOf(form.dateValue));
        LocalDate high = LocalDate.parse(textOf(form.dateMax));
        return high.isBefore(low) ? "End date is before start date" : null;
    }

    private static @Nullable String validateIsoDate(String text) {
        if (text.isEmpty()) {
            return "Enter a date (YYYY-MM-DD)";
        }
        try {
            LocalDate.parse(text);
            return null;
        }
        catch (DateTimeParseException e) {
            return "Invalid date, use YYYY-MM-DD";
        }
    }

    // --- Helpers -------------------------------------------------------------------------------------------

    private static long startOfDayMillis(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** One millisecond before the next midnight, so a BETWEEN range covers the whole end day. */
    private static long endOfDayMillis(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;
    }

    private static String isoDate(long millis) {
        return java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    private static String textOf(TextBox box) {
        String value = box.getValue();
        return value != null ? value.trim() : "";
    }

    private static boolean checked(CheckBox box) {
        return Boolean.TRUE.equals(box.getValue());
    }

    /** An IntBox mid-edit can hold no number. */
    private static long number(IntBox box, long fallback) {
        Integer value = box.getValue();
        return value != null ? value : fallback;
    }

    private static <T> T selected(ComboBox<T> combo, T fallback) {
        T value = combo.getValue();
        return value != null ? value : fallback;
    }
}
