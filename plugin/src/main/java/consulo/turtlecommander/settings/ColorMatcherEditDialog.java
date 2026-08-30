package consulo.turtlecommander.settings;

import consulo.project.Project;
import consulo.turtlecommander.ui.TextFieldContextMenuUtil;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBTextField;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.ValidationInfo;
import org.jspecify.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Edits one {@link RuleMatcher}. The kind selector flips between card-layout forms and OK reads the
 * active card into the matching variant. Created and Modified share a date card; Owner, Group and
 * Permissions share a text card.
 */
public class ColorMatcherEditDialog extends DialogWrapper {
    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB"};
    private static final long KB = 1024L;
    private static final long MB = 1024L * 1024;
    private static final long GB = 1024L * 1024 * 1024;

    /** What the user is matching on. Several kinds map onto one card. */
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

    private final ComboBox<Kind> myKindCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(Kind.values()));

    private final ComboBox<SizeOp> mySizeOpCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(SizeOp.values()));
    private final JSpinner mySizeValueSpinner =
        new JSpinner(new SpinnerNumberModel(1L, 0L, Long.MAX_VALUE, 1L));
    private final JSpinner mySizeMaxSpinner =
        new JSpinner(new SpinnerNumberModel(1L, 0L, Long.MAX_VALUE, 1L));
    private final ComboBox<String> mySizeUnitCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(SIZE_UNITS));
    private final ComboBox<String> mySizeMaxUnitCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(SIZE_UNITS));
    private final JBLabel mySizeMaxLabel = new JBLabel("And \u2264:");

    private final ComboBox<PatternKind> myNameKindCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(PatternKind.values()));
    private final JBTextField myNamePatternField = new JBTextField(24);
    private final JCheckBox myNameCaseCheck = new JCheckBox("Case sensitive");
    private final ComboBox<AppliesTo> myNameAppliesCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(AppliesTo.values()));

    private final ComboBox<PatternKind> myContainsKindCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(PatternKind.values()));
    private final JBTextField myContainsPatternField = new JBTextField(24);
    private final JCheckBox myContainsCaseCheck = new JCheckBox("Case sensitive");

    private final ComboBox<DateOp> myDateOpCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(DateOp.values()));
    private final JBLabel myDateValueLabel = new JBLabel("Date:");
    private final JBTextField myDateValueField = new JBTextField(12);
    private final JBLabel myDateMaxLabel = new JBLabel("And through:");
    private final JBTextField myDateMaxField = new JBTextField(12);
    private final JBLabel myDateAmountLabel = new JBLabel("Amount:");
    private final JSpinner myDateAmountSpinner =
        new JSpinner(new SpinnerNumberModel(7L, 1L, Long.MAX_VALUE, 1L));
    private final ComboBox<DateUnit> myDateUnitCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(DateUnit.values()));

    private final ComboBox<PatternKind> myTextKindCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(PatternKind.values()));
    private final JBTextField myTextPatternField = new JBTextField(24);
    private final JCheckBox myTextCaseCheck = new JCheckBox("Case sensitive");

    private final ComboBox<SymlinkState> mySymlinkStateCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(SymlinkState.values()));

    private final CardLayout myCards = new CardLayout();
    private final JPanel myCardPanel = new JPanel(myCards);

    private @Nullable RuleMatcher myResult;

    public ColorMatcherEditDialog(@Nullable Project project, @Nullable RuleMatcher initial) {
        super(project);
        setTitle(initial == null ? "Add matcher" : "Edit matcher");

        installLabelRenderers();
        for (JBTextField field : new JBTextField[]{myNamePatternField, myContainsPatternField,
            myTextPatternField, myDateValueField, myDateMaxField}) {
            TextFieldContextMenuUtil.installStandardContextMenu(field);
        }

        loadFrom(initial);
        myKindCombo.addActionListener(e -> showSelectedCard());
        mySizeOpCombo.addActionListener(e -> updateSizeVisibility());
        myDateOpCombo.addActionListener(e -> updateDateVisibility());

        // Picking an operator is a strong signal the user wants to type its value next.
        focusNextOnSelect(mySizeOpCombo, () -> editorField(mySizeValueSpinner));
        focusNextOnSelect(myDateOpCombo, this::visibleDateInput);
        focusNextOnSelect(myNameKindCombo, () -> myNamePatternField);
        focusNextOnSelect(myContainsKindCombo, () -> myContainsPatternField);
        focusNextOnSelect(myTextKindCombo, () -> myTextPatternField);

        init();
        initValidation();
    }

    /** Populated on OK; null when the dialog was cancelled. */
    public @Nullable RuleMatcher getResult() {
        return myResult;
    }

    private void installLabelRenderers() {
        myKindCombo.setRenderer(labelRenderer(Kind::getLabel));
        mySizeOpCombo.setRenderer(labelRenderer(SizeOp::getLabel));
        myNameKindCombo.setRenderer(labelRenderer(PatternKind::getLabel));
        myNameAppliesCombo.setRenderer(labelRenderer(AppliesTo::getLabel));
        myContainsKindCombo.setRenderer(labelRenderer(PatternKind::getLabel));
        myDateOpCombo.setRenderer(labelRenderer(DateOp::getLabel));
        myDateUnitCombo.setRenderer(labelRenderer(DateUnit::getLabel));
        myTextKindCombo.setRenderer(labelRenderer(PatternKind::getLabel));
        mySymlinkStateCombo.setRenderer(labelRenderer(SymlinkState::getLabel));
    }

    private static <T> DefaultListCellRenderer labelRenderer(Function<T, String> label) {
        return new DefaultListCellRenderer() {
            @Override
            @SuppressWarnings("unchecked")
            public Component getListCellRendererComponent(JList<?> list, @Nullable Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component component =
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value != null ? label.apply((T) value) : "");
                return component;
            }
        };
    }

    // --- Layout -------------------------------------------------------------------------------------

    @Override
    protected JComponent createCenterPanel() {
        myCardPanel.add(buildSizePanel(), "SIZE");
        myCardPanel.add(buildNamePanel(), "NAME");
        myCardPanel.add(buildDatePanel(), "DATE");
        myCardPanel.add(buildTextPanel(), "TEXT");
        myCardPanel.add(buildContainsPanel(), "CONTAINS");
        myCardPanel.add(buildSymlinkPanel(), "SYMLINK");
        myCardPanel.add(buildNamedPipePanel(), "NAMED_PIPE");

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = newConstraints();
        gbc.gridx = 0;
        top.add(new JBLabel("Match by:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        top.add(myKindCombo, gbc);

        JPanel root = new JPanel(new java.awt.BorderLayout(0, 8));
        root.setPreferredSize(new Dimension(440, 210));
        root.add(top, java.awt.BorderLayout.NORTH);
        root.add(myCardPanel, java.awt.BorderLayout.CENTER);
        showSelectedCard();
        return root;
    }

    private static GridBagConstraints newConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = JBUI.insets(2, 4);
        gbc.gridy = 0;
        return gbc;
    }

    private static void addRow(JPanel panel, GridBagConstraints gbc, int row, @Nullable JComponent label,
                               JComponent field) {
        gbc.gridy = row;
        if (label != null) {
            gbc.gridx = 0;
            panel.add(label, gbc);
        }
        gbc.gridx = 1;
        panel.add(field, gbc);
    }

    private static JPanel horizontal(JComponent first, int strut, JComponent second) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(first);
        row.add(Box.createHorizontalStrut(strut));
        row.add(second);
        return row;
    }

    private static JBLabel hint(String text) {
        JBLabel label = new JBLabel(text);
        label.setForeground(Color.GRAY);
        return label;
    }

    private JPanel buildSizePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Size"));
        GridBagConstraints gbc = newConstraints();

        mySizeValueSpinner.setPreferredSize(new Dimension(120, mySizeValueSpinner.getPreferredSize().height));
        mySizeMaxSpinner.setPreferredSize(new Dimension(120, mySizeMaxSpinner.getPreferredSize().height));
        mySizeUnitCombo.setPreferredSize(new Dimension(70, mySizeUnitCombo.getPreferredSize().height));
        mySizeMaxUnitCombo.setPreferredSize(new Dimension(70, mySizeMaxUnitCombo.getPreferredSize().height));

        addRow(panel, gbc, 0, new JBLabel("Operator:"), mySizeOpCombo);
        addRow(panel, gbc, 1, new JBLabel("Value:"), horizontal(mySizeValueSpinner, 6, mySizeUnitCombo));
        addRow(panel, gbc, 2, mySizeMaxLabel, horizontal(mySizeMaxSpinner, 6, mySizeMaxUnitCombo));
        return panel;
    }

    private JPanel buildNamePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Name"));
        GridBagConstraints gbc = newConstraints();

        addRow(panel, gbc, 0, new JBLabel("Pattern kind:"), myNameKindCombo);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        addRow(panel, gbc, 1, new JBLabel("Pattern:"), myNamePatternField);
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        addRow(panel, gbc, 2, new JBLabel("Applies to:"), myNameAppliesCombo);
        addRow(panel, gbc, 3, null, myNameCaseCheck);
        return panel;
    }

    private JPanel buildContainsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Directory contains"));
        GridBagConstraints gbc = newConstraints();

        addRow(panel, gbc, 0, new JBLabel("Pattern kind:"), myContainsKindCombo);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        addRow(panel, gbc, 1, new JBLabel("Pattern:"), myContainsPatternField);
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        addRow(panel, gbc, 2, null, myContainsCaseCheck);
        return panel;
    }

    private JPanel buildDatePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Date"));
        GridBagConstraints gbc = newConstraints();

        myDateAmountSpinner.setPreferredSize(
            new Dimension(90, myDateAmountSpinner.getPreferredSize().height));

        addRow(panel, gbc, 0, new JBLabel("Operator:"), myDateOpCombo);
        addRow(panel, gbc, 1, myDateValueLabel, myDateValueField);
        addRow(panel, gbc, 2, myDateMaxLabel, myDateMaxField);
        addRow(panel, gbc, 3, myDateAmountLabel, horizontal(myDateAmountSpinner, 6, myDateUnitCombo));
        addRow(panel, gbc, 4, null, hint("Dates are YYYY-MM-DD"));
        return panel;
    }

    private JPanel buildTextPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Value"));
        GridBagConstraints gbc = newConstraints();

        addRow(panel, gbc, 0, new JBLabel("Pattern kind:"), myTextKindCombo);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        addRow(panel, gbc, 1, new JBLabel("Pattern:"), myTextPatternField);
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        addRow(panel, gbc, 2, null, myTextCaseCheck);
        addRow(panel, gbc, 3, null, hint("e.g. perms glob *w* or owner = root"));
        return panel;
    }

    private JPanel buildSymlinkPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Symbolic link"));
        GridBagConstraints gbc = newConstraints();

        addRow(panel, gbc, 0, new JBLabel("State:"), mySymlinkStateCombo);
        addRow(panel, gbc, 1, null, hint("Matches symlinks; \"broken\" = target missing"));
        return panel;
    }

    private JPanel buildNamedPipePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Named pipe"));
        GridBagConstraints gbc = newConstraints();
        gbc.gridx = 0;

        // No fields: an entry either is a FIFO or it isn't. Explain the Unix-only scope instead.
        panel.add(hint("Matches Unix named pipes (FIFOs). No options."), gbc);
        gbc.gridy = 1;
        panel.add(hint("Never matches on Windows."), gbc);
        return panel;
    }

    // --- Card and field visibility --------------------------------------------------------------------

    private void showSelectedCard() {
        myCards.show(myCardPanel, selectedKind().cardName());
        updateSizeVisibility();
        updateDateVisibility();
    }

    private void updateSizeVisibility() {
        boolean between = mySizeOpCombo.getSelectedItem() == SizeOp.BETWEEN;
        mySizeMaxLabel.setVisible(between);
        mySizeMaxSpinner.setVisible(between);
        mySizeMaxUnitCombo.setVisible(between);
    }

    /** Absolute operators need dates; the relative ones need an amount and a unit. */
    private void updateDateVisibility() {
        if (!(myDateOpCombo.getSelectedItem() instanceof DateOp op)) {
            return;
        }
        boolean absolute = op == DateOp.BEFORE || op == DateOp.AFTER || op == DateOp.BETWEEN;
        boolean between = op == DateOp.BETWEEN;
        boolean relative = op == DateOp.WITHIN_LAST || op == DateOp.OLDER_THAN;

        myDateValueLabel.setVisible(absolute);
        myDateValueField.setVisible(absolute);
        myDateMaxLabel.setVisible(between);
        myDateMaxField.setVisible(between);
        myDateAmountLabel.setVisible(relative);
        myDateAmountSpinner.setVisible(relative);
        myDateUnitCombo.setVisible(relative);
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        // Focus the value the user came to type, not the kind selector they already set.
        return switch (selectedKind()) {
            case SIZE -> editorField(mySizeValueSpinner);
            case NAME -> myNamePatternField;
            case CONTAINS -> myContainsPatternField;
            case OWNER, GROUP, PERMISSIONS -> myTextPatternField;
            case CREATED, MODIFIED -> {
                JComponent input = visibleDateInput();
                yield input != null ? input : myDateOpCombo;
            }
            case SYMLINK -> mySymlinkStateCombo;
            case NAMED_PIPE -> myKindCombo;
        };
    }

    private @Nullable JComponent visibleDateInput() {
        if (myDateValueField.isVisible()) {
            return myDateValueField;
        }
        return myDateAmountSpinner.isVisible() ? editorField(myDateAmountSpinner) : null;
    }

    /**
     * Moves focus to the next input after the user picks from this combo's popup. Gated on the
     * popup being the source, so plain arrow-key navigation through a closed combo doesn't keep
     * stealing focus on every selection change.
     */
    private static void focusNextOnSelect(ComboBox<?> combo, Supplier<@Nullable JComponent> next) {
        boolean[] fromPopup = {false};
        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                fromPopup[0] = true;
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                fromPopup[0] = false;
            }
        });
        combo.addActionListener(e -> {
            if (fromPopup[0]) {
                fromPopup[0] = false;
                SwingUtilities.invokeLater(() -> {
                    JComponent target = next.get();
                    if (target != null) {
                        target.requestFocusInWindow();
                    }
                });
            }
        });
    }

    /** A spinner's inner text field, so focus lands on the editable value rather than the shell. */
    private static JComponent editorField(JSpinner spinner) {
        return ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
    }

    // --- Loading and saving ------------------------------------------------------------------------------

    private Kind selectedKind() {
        Object selected = myKindCombo.getSelectedItem();
        return selected instanceof Kind kind ? kind : Kind.CONTAINS;
    }

    private void loadFrom(@Nullable RuleMatcher matcher) {
        if (matcher == null) {
            loadDefaults();
            return;
        }
        myKindCombo.setSelectedItem(Kind.from(matcher));

        switch (matcher) {
            case RuleMatcher.Size size -> {
                mySizeOpCombo.setSelectedItem(size.op());
                applySplitSize(size.bytes(), mySizeValueSpinner, mySizeUnitCombo);
                applySplitSize(size.bytesMax(), mySizeMaxSpinner, mySizeMaxUnitCombo);
            }
            case RuleMatcher.Name name -> {
                myNameKindCombo.setSelectedItem(name.kind());
                myNamePatternField.setText(name.pattern());
                myNameCaseCheck.setSelected(name.caseSensitive());
                myNameAppliesCombo.setSelectedItem(name.appliesTo());
            }
            case RuleMatcher.Contains contains -> {
                myContainsKindCombo.setSelectedItem(contains.kind());
                myContainsPatternField.setText(contains.pattern());
                myContainsCaseCheck.setSelected(contains.caseSensitive());
            }
            case RuleMatcher.Date date -> {
                myDateOpCombo.setSelectedItem(date.op());
                myDateValueField.setText(date.epochMillis() != 0L ? isoDate(date.epochMillis()) : "");
                myDateMaxField.setText(date.epochMillisMax() != 0L ? isoDate(date.epochMillisMax()) : "");
                myDateAmountSpinner.setValue(date.amount() > 0L ? date.amount() : 7L);
                myDateUnitCombo.setSelectedItem(date.unit());
            }
            case RuleMatcher.Text text -> {
                myTextKindCombo.setSelectedItem(text.kind());
                myTextPatternField.setText(text.pattern());
                myTextCaseCheck.setSelected(text.caseSensitive());
            }
            case RuleMatcher.Symlink symlink -> mySymlinkStateCombo.setSelectedItem(symlink.state());
            case RuleMatcher.NamedPipe ignored -> {
                // Nothing to load: the matcher carries no options.
            }
        }
    }

    /** Sensible starting points for a brand-new matcher, so no card opens empty. */
    private void loadDefaults() {
        myKindCombo.setSelectedItem(Kind.CONTAINS);
        myContainsKindCombo.setSelectedItem(PatternKind.EXACT);
        mySizeUnitCombo.setSelectedItem("MB");
        mySizeMaxUnitCombo.setSelectedItem("MB");
        myNameAppliesCombo.setSelectedItem(AppliesTo.BOTH);
        myDateOpCombo.setSelectedItem(DateOp.WITHIN_LAST);
        myDateUnitCombo.setSelectedItem(DateUnit.DAYS);
        myTextKindCombo.setSelectedItem(PatternKind.EXACT);
    }

    /** Shows a byte count in the largest unit that divides it exactly, as the user entered it. */
    private static void applySplitSize(long bytes, JSpinner spinner, ComboBox<String> unitCombo) {
        if (bytes != 0L && bytes % GB == 0) {
            spinner.setValue(bytes / GB);
            unitCombo.setSelectedItem("GB");
        }
        else if (bytes != 0L && bytes % MB == 0) {
            spinner.setValue(bytes / MB);
            unitCombo.setSelectedItem("MB");
        }
        else if (bytes != 0L && bytes % KB == 0) {
            spinner.setValue(bytes / KB);
            unitCombo.setSelectedItem("KB");
        }
        else {
            spinner.setValue(bytes);
            unitCombo.setSelectedItem("B");
        }
    }

    private static long toBytes(long value, @Nullable Object unit) {
        return switch (String.valueOf(unit)) {
            case "KB" -> value * KB;
            case "MB" -> value * MB;
            case "GB" -> value * GB;
            default -> value;
        };
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        return switch (selectedKind()) {
            case NAME -> validatePattern(selected(myNameKindCombo), myNamePatternField);
            case CONTAINS -> validatePattern(selected(myContainsKindCombo), myContainsPatternField);
            case OWNER, GROUP, PERMISSIONS -> validatePattern(selected(myTextKindCombo), myTextPatternField);
            case CREATED, MODIFIED -> validateDateCard();
            case SIZE, SYMLINK, NAMED_PIPE -> null;
        };
    }

    @Override
    protected void doOKAction() {
        myResult = switch (selectedKind()) {
            case SIZE -> buildSizeMatcher();
            case NAME -> new RuleMatcher.Name(selected(myNameKindCombo),
                myNamePatternField.getText().trim(), myNameCaseCheck.isSelected(),
                selected(myNameAppliesCombo));
            case CONTAINS -> new RuleMatcher.Contains(selected(myContainsKindCombo),
                myContainsPatternField.getText().trim(), myContainsCaseCheck.isSelected());
            case CREATED -> buildDateMatcher(DateField.CREATED);
            case MODIFIED -> buildDateMatcher(DateField.MODIFIED);
            case OWNER -> buildTextMatcher(TextProperty.OWNER);
            case GROUP -> buildTextMatcher(TextProperty.GROUP);
            case PERMISSIONS -> buildTextMatcher(TextProperty.PERMISSIONS);
            case SYMLINK -> new RuleMatcher.Symlink(selected(mySymlinkStateCombo));
            case NAMED_PIPE -> new RuleMatcher.NamedPipe();
        };
        super.doOKAction();
    }

    private RuleMatcher.Size buildSizeMatcher() {
        SizeOp op = selected(mySizeOpCombo);
        long bytes = toBytes(((Number) mySizeValueSpinner.getValue()).longValue(),
            mySizeUnitCombo.getSelectedItem());
        long bytesMax = op == SizeOp.BETWEEN
            ? toBytes(((Number) mySizeMaxSpinner.getValue()).longValue(), mySizeMaxUnitCombo.getSelectedItem())
            : 0L;
        return new RuleMatcher.Size(op, bytes, bytesMax);
    }

    /** Reads the date card; {@link #doValidate} has already vetted the fields. */
    private RuleMatcher.Date buildDateMatcher(DateField field) {
        DateOp op = selected(myDateOpCombo);
        return switch (op) {
            case BEFORE, AFTER -> new RuleMatcher.Date(field, op,
                startOfDayMillis(LocalDate.parse(myDateValueField.getText().trim())), 0L, 0L, DateUnit.DAYS);
            case BETWEEN -> new RuleMatcher.Date(field, op,
                startOfDayMillis(LocalDate.parse(myDateValueField.getText().trim())),
                endOfDayMillis(LocalDate.parse(myDateMaxField.getText().trim())), 0L, DateUnit.DAYS);
            case WITHIN_LAST, OLDER_THAN -> new RuleMatcher.Date(field, op, 0L, 0L,
                ((Number) myDateAmountSpinner.getValue()).longValue(), selected(myDateUnitCombo));
        };
    }

    private RuleMatcher.Text buildTextMatcher(TextProperty field) {
        return new RuleMatcher.Text(field, selected(myTextKindCombo),
            myTextPatternField.getText().trim(), myTextCaseCheck.isSelected());
    }

    // --- Validation -------------------------------------------------------------------------------------

    /** A pattern must be non-empty and compile, so a broken rule can't be saved and silently never match. */
    private static @Nullable ValidationInfo validatePattern(PatternKind kind, JBTextField field) {
        String pattern = field.getText().trim();
        if (pattern.isEmpty()) {
            return new ValidationInfo("Pattern cannot be empty", field);
        }
        if (kind == PatternKind.EXACT) {
            return null;
        }
        try {
            Pattern.compile(kind == PatternKind.GLOB ? NamePatterns.globToRegex(pattern) : pattern);
            return null;
        }
        catch (PatternSyntaxException e) {
            return new ValidationInfo(kind == PatternKind.GLOB
                ? "Invalid glob pattern"
                : "Invalid regular expression: " + e.getDescription(), field);
        }
    }

    private @Nullable ValidationInfo validateDateCard() {
        DateOp op = selected(myDateOpCombo);
        return switch (op) {
            case BEFORE, AFTER -> validateIsoDate(myDateValueField);
            case BETWEEN -> validateDateRange();
            case WITHIN_LAST, OLDER_THAN ->
                ((Number) myDateAmountSpinner.getValue()).longValue() <= 0L
                    ? new ValidationInfo("Amount must be positive", editorField(myDateAmountSpinner))
                    : null;
        };
    }

    private @Nullable ValidationInfo validateDateRange() {
        ValidationInfo start = validateIsoDate(myDateValueField);
        if (start != null) {
            return start;
        }
        ValidationInfo end = validateIsoDate(myDateMaxField);
        if (end != null) {
            return end;
        }
        LocalDate low = LocalDate.parse(myDateValueField.getText().trim());
        LocalDate high = LocalDate.parse(myDateMaxField.getText().trim());
        return high.isBefore(low)
            ? new ValidationInfo("End date is before start date", myDateMaxField)
            : null;
    }

    private static @Nullable ValidationInfo validateIsoDate(JBTextField field) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return new ValidationInfo("Enter a date (YYYY-MM-DD)", field);
        }
        try {
            LocalDate.parse(text);
            return null;
        }
        catch (DateTimeParseException e) {
            return new ValidationInfo("Invalid date, use YYYY-MM-DD", field);
        }
    }

    // --- Date helpers -------------------------------------------------------------------------------------

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

    @SuppressWarnings("unchecked")
    private static <T> T selected(ComboBox<T> combo) {
        return (T) combo.getSelectedItem();
    }
}
