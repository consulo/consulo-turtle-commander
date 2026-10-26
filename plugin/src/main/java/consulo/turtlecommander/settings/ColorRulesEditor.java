package consulo.turtlecommander.settings;

import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserFactory;
import consulo.project.Project;
import consulo.turtlecommander.dialog.InputDialog;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * The rule table and its toolbar, embedded in the settings page.
 *
 * <p>Holds its own mutable copy of the rules so Apply/Cancel behave the way they do for themes and
 * columns: edits stay local until {@link #applyTo} writes them back.
 */
public class ColorRulesEditor {
    private static final int COL_ACTIVE = 0;
    private static final int COL_PRIORITY = 1;
    private static final int COL_NAME = 2;
    private static final int COL_COLOR = 3;
    private static final int COL_BG = 4;
    private static final int COL_DOT = 5;
    private static final int COL_ICON = 6;
    private static final int COL_MATCHERS = 7;
    private static final int COLUMN_COUNT = 8;

    /**
     * Display and evaluation order: highest priority first — the winner in WINNER mode, the top
     * overlay in LAYERED — with ties broken by name so the order is stable across restarts.
     */
    private static final Comparator<ColorRule> BY_PRIORITY =
        Comparator.comparingInt(ColorRule::priority).reversed()
            .thenComparing(rule -> rule.name().toLowerCase(java.util.Locale.ROOT));

    private final @Nullable Project myProject;

    private final List<ColorRule> myRules = new ArrayList<>();
    private List<ColorRule> mySavedSnapshot = List.of();
    private ColorizationMode mySavedMode = ColorizationMode.WINNER;

    private final RulesTableModel myTableModel = new RulesTableModel();
    private final JBTable myTable = new JBTable(myTableModel);
    private final ComboBox<ColorizationMode> myModeCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(ColorizationMode.values()));
    private final JPanel myPanel = new JPanel(new BorderLayout(4, 4));

    public ColorRulesEditor(@Nullable Project project) {
        myProject = project;

        myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Whole-row selection: the highlight should span the rule, not a single cell.
        myTable.setRowSelectionAllowed(true);
        myTable.setColumnSelectionAllowed(false);
        myTable.setRowHeight(JBUI.scale(24));
        myTable.getTableHeader().setReorderingAllowed(false);
        myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        configureColumns();

        myModeCombo.setRenderer(new ModeRenderer());
        myTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleActiveIfOnCheckBox(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getClickCount() == 2 && myTable.rowAtPoint(e.getPoint()) >= 0) {
                    editSelected();
                }
            }
        });

        myPanel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        myPanel.setBorder(BorderFactory.createTitledBorder("File colors and icons"));
        myPanel.add(buildToolbar(), BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(myTable);
        scrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(240)));
        myPanel.add(scrollPane, BorderLayout.CENTER);
        myPanel.add(buildRightButtons(), BorderLayout.EAST);
    }

    public JPanel getPanel() {
        return myPanel;
    }

    public void resetFrom(TurtleCommanderSettings.State state) {
        myRules.clear();
        myRules.addAll(ColorRuleManager.getAllRules(state));
        myRules.sort(BY_PRIORITY);
        mySavedSnapshot = List.copyOf(myRules);
        mySavedMode = ColorRuleManager.getMode(state);
        myModeCombo.setSelectedItem(mySavedMode);
        myTableModel.fireTableDataChanged();
    }

    public void applyTo(TurtleCommanderSettings.State state) {
        state.colorRulesInitialized = true;
        state.colorRules.clear();
        for (ColorRule rule : myRules) {
            state.colorRules.add(SavedColorRule.fromRule(rule));
        }
        ColorRuleManager.setMode(state, selectedMode());
        mySavedSnapshot = List.copyOf(myRules);
        mySavedMode = selectedMode();
    }

    public boolean isModified() {
        return selectedMode() != mySavedMode || !myRules.equals(mySavedSnapshot);
    }

    private ColorizationMode selectedMode() {
        Object selected = myModeCombo.getSelectedItem();
        return selected instanceof ColorizationMode mode ? mode : ColorizationMode.WINNER;
    }

    // --- Toolbar and buttons ---------------------------------------------------------------------

    private JComponent buildToolbar() {
        JButton exportButton = new JButton("Export...");
        exportButton.setToolTipText("Export current rules to a .tcrules file");
        exportButton.addActionListener(e -> exportCurrentRules());

        JButton importButton = new JButton("Import...");
        importButton.setToolTipText("Import rules from a .tcrules file");
        importButton.addActionListener(e -> importRules());

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.add(new JBLabel("Mode:"));
        row.add(myModeCombo);
        row.add(Box.createHorizontalStrut(12));
        row.add(exportButton);
        row.add(importButton);
        return row;
    }

    private JComponent buildRightButtons() {
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        addButton(column, "Add...", null, this::addRule);
        addButton(column, "Edit...", null, this::editSelected);
        addButton(column, "Duplicate", null, this::duplicateSelected);
        addButton(column, "Delete", null, this::deleteSelected);
        addButton(column, "Up", "Raise priority (move up)", () -> moveSelected(-1));
        addButton(column, "Down", "Lower priority (move down)", () -> moveSelected(1));
        addButton(column, "Reset to defaults", null, this::resetToDefaults);
        return column;
    }

    private static void addButton(JPanel column, String text, @Nullable String tooltip, Runnable action) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
        button.addActionListener(e -> action.run());
        column.add(button);
        column.add(Box.createVerticalStrut(4));
    }

    // --- Rule mutations ---------------------------------------------------------------------------

    private void addRule() {
        ColorRuleEditDialog dialog = new ColorRuleEditDialog(myProject, null);
        if (dialog.showAndGet() && dialog.getResult() != null) {
            ColorRule rule = dialog.getResult();
            myRules.add(rule);
            resortAndSelect(rule.id());
        }
    }

    private void editSelected() {
        int index = myTable.getSelectedRow();
        if (index < 0) {
            return;
        }
        ColorRuleEditDialog dialog = new ColorRuleEditDialog(myProject, myRules.get(index));
        if (dialog.showAndGet() && dialog.getResult() != null) {
            ColorRule rule = dialog.getResult();
            myRules.set(index, rule);
            resortAndSelect(rule.id());
        }
    }

    private void duplicateSelected() {
        int index = myTable.getSelectedRow();
        if (index < 0) {
            return;
        }
        ColorRule source = myRules.get(index);
        ColorRule copy = new ColorRule(UUID.randomUUID().toString(), source.name() + " (copy)",
            source.priority(), source.active(), source.combinator(), source.matchers(), source.style());
        myRules.add(copy);
        resortAndSelect(copy.id());
    }

    private void deleteSelected() {
        int index = myTable.getSelectedRow();
        if (index < 0) {
            return;
        }
        ColorRule rule = myRules.get(index);
        if (Messages.showYesNoDialog("Delete rule \"" + rule.name() + "\"?", "Delete Rule", null)
            != Messages.YES) {
            return;
        }
        myRules.remove(index);
        myTableModel.fireTableDataChanged();
    }

    private void resetToDefaults() {
        if (Messages.showYesNoDialog("Replace all rules with the built-in defaults?", "Reset Rules", null)
            != Messages.YES) {
            return;
        }
        myRules.clear();
        myRules.addAll(ColorRuleDefaults.builtinRules());
        resortAndSelect(null);
    }

    /**
     * Swaps the selected rule with its neighbour in the priority order. The new priority is set just
     * past the neighbour's rather than nudged by one, so a single click always reorders even when
     * priorities are spaced far apart.
     */
    private void moveSelected(int direction) {
        int index = myTable.getSelectedRow();
        int target = index + direction;
        if (index < 0 || target < 0 || target >= myRules.size()) {
            return;
        }
        ColorRule current = myRules.get(index);
        ColorRule neighbour = myRules.get(target);
        int newPriority = direction < 0 ? neighbour.priority() + 1 : neighbour.priority() - 1;
        myRules.set(index, new ColorRule(current.id(), current.name(), newPriority, current.active(),
            current.combinator(), current.matchers(), current.style()));
        resortAndSelect(current.id());
    }

    private void toggleActiveIfOnCheckBox(MouseEvent e) {
        if (myTable.columnAtPoint(e.getPoint()) != COL_ACTIVE) {
            return;
        }
        int row = myTable.rowAtPoint(e.getPoint());
        if (row < 0) {
            return;
        }
        ColorRule current = myRules.get(row);
        myRules.set(row, new ColorRule(current.id(), current.name(), current.priority(), !current.active(),
            current.combinator(), current.matchers(), current.style()));
        myTableModel.fireTableRowsUpdated(row, row);
    }

    /**
     * Restores the priority ordering after a mutation and re-selects the rule by id — its row moves
     * when its priority changes. The list stays sorted so the table always shows evaluation order.
     */
    private void resortAndSelect(@Nullable String selectId) {
        myRules.sort(BY_PRIORITY);
        myTableModel.fireTableDataChanged();
        if (selectId == null) {
            return;
        }
        for (int i = 0; i < myRules.size(); i++) {
            if (myRules.get(i).id().equals(selectId)) {
                myTable.setRowSelectionInterval(i, i);
                return;
            }
        }
    }

    // --- Import and export -------------------------------------------------------------------------

    private void exportCurrentRules() {
        InputDialog dialog = new InputDialog(myProject, "Export Rules", "Config name:", "");
        if (!dialog.showAndGet()) {
            return;
        }
        String name = dialog.getInputValue();
        if (name.isBlank()) {
            Messages.showWarningDialog("Config name cannot be empty.", "Export Rules");
            return;
        }

        FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        descriptor.setTitle("Export Rules");
        descriptor.setDescription("Choose the directory to write the .tcrules file into");
        FileChooserFactory.getInstance().newBuilder(descriptor).project(myProject).showSingle().whenComplete((directory, error) -> {
            if (directory == null) {
                return;
            }

            String text = ColorRulesIO.export(new ColorRulesConfig(name, List.copyOf(myRules)));
            Path target = directory.resolve(name.replace(' ', '_') + ".tcrules");
            try {
                Files.writeString(target, text, StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                Messages.showWarningDialog("Failed to write " + target + ": " + e.getMessage(), "Export Rules");
            }
        });
    }

    private void importRules() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
        descriptor.setTitle("Import Rules");
        descriptor.setDescription("Select a Turtle Commander rules file");
        FileChooserFactory.getInstance().newBuilder(descriptor).project(myProject).showSingle().whenComplete((chosen, error) -> {
            if (chosen == null) {
                return;
            }

            ColorRulesConfig config;
            try {
                config = ColorRulesIO.parse(Files.readString(chosen, StandardCharsets.UTF_8));
            }
            catch (IOException e) {
                Messages.showWarningDialog("Failed to read " + chosen + ": " + e.getMessage(),
                    "Import Rules");
                return;
            }
            if (config == null) {
                Messages.showWarningDialog("Invalid rules file.", "Import Rules");
                return;
            }

            int choice = Messages.showYesNoCancelDialog(
                "Import \"" + config.name() + "\" (" + config.rules().size() + " rules)?\n\n"
                    + "Yes = replace current rules\nNo = append to current rules",
                "Import Rules", "Replace", "Append", "Cancel", null);
            if (choice == Messages.CANCEL) {
                return;
            }
            if (choice == Messages.YES) {
                myRules.clear();
            }
            // Fresh ids, so an imported rule can never collide with — or silently overwrite — an existing one.
            for (ColorRule rule : config.rules()) {
                myRules.add(ColorRuleDefaults.withId(rule, UUID.randomUUID().toString()));
            }
            resortAndSelect(null);
        });
    }

    // --- Table -------------------------------------------------------------------------------------

    private void configureColumns() {
        TableColumnModel cm = myTable.getColumnModel();
        setColumnWidth(cm, COL_ACTIVE, 50, 60, new ActiveCellRenderer());
        setColumnWidth(cm, COL_PRIORITY, 60, 80, new CenterAlignedRenderer());
        setColumnWidth(cm, COL_NAME, 220, 0, new NoFocusRenderer());
        setColumnWidth(cm, COL_COLOR, 50, 60, new ColorSwatchCellRenderer());
        setColumnWidth(cm, COL_BG, 50, 60, new ColorSwatchCellRenderer());
        setColumnWidth(cm, COL_DOT, 50, 60, new ColorSwatchCellRenderer());
        setColumnWidth(cm, COL_ICON, 50, 60, new IconCellRenderer());
        setColumnWidth(cm, COL_MATCHERS, 300, 0, new NoFocusRenderer());
    }

    private static void setColumnWidth(TableColumnModel cm, int index, int preferred, int max,
                                       DefaultTableCellRenderer renderer) {
        cm.getColumn(index).setPreferredWidth(JBUI.scale(preferred));
        if (max > 0) {
            cm.getColumn(index).setMaxWidth(JBUI.scale(max));
        }
        cm.getColumn(index).setCellRenderer(renderer);
    }

    private final class RulesTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return myRules.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_COUNT;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case COL_ACTIVE -> "On";
                case COL_PRIORITY -> "Prio";
                case COL_NAME -> "Name";
                case COL_COLOR -> "Fg";
                case COL_BG -> "Bg";
                case COL_DOT -> "Dot";
                case COL_ICON -> "Icon";
                case COL_MATCHERS -> "Matchers";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case COL_ACTIVE -> Boolean.class;
                case COL_PRIORITY -> Integer.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ColorRule rule = myRules.get(rowIndex);
            return switch (columnIndex) {
                case COL_ACTIVE -> rule.active();
                case COL_PRIORITY -> rule.priority();
                case COL_NAME -> rule.name();
                case COL_COLOR -> rule.style().fontColor();
                case COL_BG -> rule.style().backgroundColor();
                case COL_DOT -> rule.style().iconDotColor();
                case COL_ICON -> rule.style().iconId();
                case COL_MATCHERS -> summarizeMatchers(rule);
                default -> "";
            };
        }
    }

    private static String summarizeMatchers(ColorRule rule) {
        if (rule.matchers().isEmpty()) {
            return "(none)";
        }
        StringJoiner joiner =
            new StringJoiner(rule.combinator() == Combinator.AND ? " AND " : " OR ");
        for (RuleMatcher matcher : rule.matchers()) {
            joiner.add(RuleMatcherDescriptions.describe(matcher));
        }
        return joiner.toString();
    }

    private static final class ModeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, @Nullable Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Component component =
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ColorizationMode mode) {
                setText(mode == ColorizationMode.WINNER
                    ? "Winner takes all (highest priority)"
                    : "Layered (overlay by priority)");
            }
            return component;
        }
    }

    private static final class ActiveCellRenderer extends DefaultTableCellRenderer {
        private final JCheckBox myCheckBox = new JCheckBox();

        ActiveCellRenderer() {
            myCheckBox.setHorizontalAlignment(CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, @Nullable Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            myCheckBox.setSelected(Boolean.TRUE.equals(value));
            myCheckBox.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            myCheckBox.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            myCheckBox.setOpaque(true);
            return myCheckBox;
        }
    }

    /**
     * Drops the per-cell focus rectangle: with whole-row selection the focused-cell border reads as
     * a single selected cell rather than a selected rule.
     */
    private static class NoFocusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, @Nullable Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            return super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
        }
    }

    private static final class CenterAlignedRenderer extends NoFocusRenderer {
        CenterAlignedRenderer() {
            setHorizontalAlignment(CENTER);
        }
    }

    /** Shows a rule's colour as a swatch; an unset colour renders as an empty cell. */
    private static final class ColorSwatchCellRenderer extends NoFocusRenderer {
        ColorSwatchCellRenderer() {
            setHorizontalAlignment(CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, @Nullable Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component component =
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String hex = value instanceof String text ? text : "";
            Color color = PresetColors.decode(hex);
            setText("");
            setIcon(color != null ? TargetAWT.to(ColorSwatches.swatch(color)) : null);
            setToolTipText(color != null ? hex : null);
            return component;
        }
    }

    /** Shows a rule's override icon, or nothing when it doesn't set one. */
    private static final class IconCellRenderer extends NoFocusRenderer {
        IconCellRenderer() {
            setHorizontalAlignment(CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, @Nullable Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component component =
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String id = value instanceof String text ? text : "";
            Image icon = RuleIcons.resolve(id);
            setText("");
            setIcon(icon != null ? TargetAWT.to(icon) : null);
            setToolTipText(id.isEmpty() ? null : RuleIcons.label(id));
            return component;
        }
    }
}
