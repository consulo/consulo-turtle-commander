package consulo.turtlecommander.settings;

import consulo.project.Project;
import consulo.turtlecommander.ui.TextFieldContextMenuUtil;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBTextField;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Edits one colourisation rule: its identity, its matchers, and the style it applies. Matchers are
 * edited through {@link ColorMatcherEditDialog}; the colour pickers are the same ones the theme
 * editor uses, so the settings surface stays consistent.
 */
public class ColorRuleEditDialog extends DialogWrapper {
    /** The "Default" entry of the font-style combo, meaning "don't override". */
    private static final int FONT_STYLE_DEFAULT_INDEX = 0;
    private static final String[] FONT_STYLE_LABELS =
        {"Default", "Plain", "Bold", "Italic", "Bold Italic"};

    private final @Nullable Project myProject;
    private final String myId;

    private final JBTextField myNameField = new JBTextField(24);
    private final JSpinner myPrioritySpinner =
        new JSpinner(new SpinnerNumberModel(0, -1_000_000, 1_000_000, 1));
    private final JCheckBox myActiveCheck = new JCheckBox("Active");
    private final ComboBox<Combinator> myCombinatorCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(Combinator.values()));

    private final List<RuleMatcher> myMatchers = new ArrayList<>();
    private final MatcherTableModel myMatcherModel = new MatcherTableModel();
    private final JBTable myMatcherTable = new JBTable(myMatcherModel);

    private final ColorPickerButton myFontColorButton = new ColorPickerButton();
    private final ColorPickerButton myBackgroundColorButton = new ColorPickerButton();
    private final ColorPickerButton myDotColorButton = new ColorPickerButton();
    private final IconPickerButton myIconPickerButton = new IconPickerButton();
    private final ComboBox<String> myFontStyleCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(FONT_STYLE_LABELS));
    private final JSpinner myFontSizeSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 72, 1));
    private final JCheckBox myStrikethroughCheck = new JCheckBox("Strikethrough");
    private final JBLabel myPreviewLabel = new JBLabel("Preview");

    private @Nullable ColorRule myResult;

    public ColorRuleEditDialog(@Nullable Project project, @Nullable ColorRule initial) {
        super(project);
        myProject = project;
        myId = initial != null ? initial.id() : UUID.randomUUID().toString();

        setTitle(initial == null ? "Add color rule" : "Edit color rule");
        myCombinatorCombo.setRenderer(new CombinatorRenderer());
        configureMatcherTable();
        configurePreviewLabel();
        TextFieldContextMenuUtil.installStandardContextMenu(myNameField);

        loadFrom(initial);
        wireLivePreview();
        init();
    }

    /** Populated on OK; null when the dialog was cancelled. */
    public @Nullable ColorRule getResult() {
        return myResult;
    }

    private void configureMatcherTable() {
        myMatcherTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        myMatcherTable.setRowSelectionAllowed(true);
        myMatcherTable.setColumnSelectionAllowed(false);
        myMatcherTable.setRowHeight(JBUI.scale(22));
        myMatcherTable.getTableHeader().setReorderingAllowed(false);

        // Whole-row selection: without dropping the focus rectangle a focused cell reads as a
        // single selected cell rather than the selected matcher.
        DefaultTableCellRenderer noFocusRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, @Nullable Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                return super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            }
        };
        myMatcherTable.getColumnModel().getColumn(0).setPreferredWidth(JBUI.scale(120));
        myMatcherTable.getColumnModel().getColumn(0).setCellRenderer(noFocusRenderer);
        myMatcherTable.getColumnModel().getColumn(1).setPreferredWidth(JBUI.scale(420));
        myMatcherTable.getColumnModel().getColumn(1).setCellRenderer(noFocusRenderer);

        myMatcherTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getClickCount() == 2 && myMatcherTable.rowAtPoint(e.getPoint()) >= 0) {
                    editSelectedMatcher();
                }
            }
        });
    }

    private void configurePreviewLabel() {
        Color separator = UIManager.getColor("Separator.foreground");
        myPreviewLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(separator != null ? separator : JBColor.GRAY),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        myPreviewLabel.setOpaque(true);
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel matchersPanel = new JPanel(new BorderLayout(4, 4));
        matchersPanel.setBorder(BorderFactory.createTitledBorder("Matchers"));
        JScrollPane matcherScroll = new JScrollPane(myMatcherTable);
        matcherScroll.setPreferredSize(new Dimension(550, 140));
        matchersPanel.add(matcherScroll, BorderLayout.CENTER);
        matchersPanel.add(buildMatcherButtons(), BorderLayout.EAST);

        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder("Preview"));
        previewPanel.add(myPreviewLabel, BorderLayout.CENTER);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.add(buildHeader());
        root.add(Box.createVerticalStrut(6));
        root.add(matchersPanel);
        root.add(Box.createVerticalStrut(6));
        root.add(buildStylePanel());
        root.add(Box.createVerticalStrut(6));
        root.add(previewPanel);
        root.setPreferredSize(new Dimension(640, 560));
        return root;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = JBUI.insets(2, 4);

        gbc.gridy = 0;
        gbc.gridx = 0;
        header.add(new JBLabel("Name:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        header.add(myNameField, gbc);
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;

        myPrioritySpinner.setPreferredSize(new Dimension(80, myPrioritySpinner.getPreferredSize().height));
        JPanel priorityRow = new JPanel();
        priorityRow.setLayout(new BoxLayout(priorityRow, BoxLayout.X_AXIS));
        priorityRow.add(myPrioritySpinner);
        priorityRow.add(Box.createHorizontalStrut(16));
        priorityRow.add(myActiveCheck);

        gbc.gridy = 1;
        gbc.gridx = 0;
        header.add(new JBLabel("Priority:"), gbc);
        gbc.gridx = 1;
        header.add(priorityRow, gbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        header.add(new JBLabel("Combine with:"), gbc);
        gbc.gridx = 1;
        header.add(myCombinatorCombo, gbc);
        return header;
    }

    private JComponent buildMatcherButtons() {
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        addMatcherButton(column, "Add...", this::addMatcher);
        addMatcherButton(column, "Edit...", this::editSelectedMatcher);
        addMatcherButton(column, "Delete", this::deleteSelectedMatcher);
        return column;
    }

    private static void addMatcherButton(JPanel column, String text, Runnable action) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
        button.addActionListener(e -> action.run());
        column.add(button);
        column.add(Box.createVerticalStrut(4));
    }

    private JPanel buildStylePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Style"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = JBUI.insets(2, 4);

        addStyleRow(panel, gbc, 0, "Font color:", myFontColorButton);
        addStyleRow(panel, gbc, 1, "Background:", myBackgroundColorButton);
        addStyleRow(panel, gbc, 2, "Icon dot:", myDotColorButton);
        addStyleRow(panel, gbc, 3, "Icon:",
            labelled(myIconPickerButton, "(replaces the file-type icon)"));
        addStyleRow(panel, gbc, 4, "Font style:", myFontStyleCombo);

        myFontSizeSpinner.setPreferredSize(new Dimension(60, myFontSizeSpinner.getPreferredSize().height));
        addStyleRow(panel, gbc, 5, "Font size override:",
            labelled(myFontSizeSpinner, "(0 = no override)"));
        addStyleRow(panel, gbc, 6, "Effects:", myStrikethroughCheck);
        return panel;
    }

    private static void addStyleRow(JPanel panel, GridBagConstraints gbc, int row, String label,
                                    JComponent field) {
        gbc.gridy = row;
        gbc.gridx = 0;
        panel.add(new JBLabel(label), gbc);
        gbc.gridx = 1;
        panel.add(field, gbc);
    }

    private static JPanel labelled(JComponent field, String hint) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(field);
        row.add(Box.createHorizontalStrut(6));
        row.add(new JBLabel(hint));
        return row;
    }

    // --- Live preview ------------------------------------------------------------------------------

    private void wireLivePreview() {
        myFontColorButton.setOnChange(this::refreshPreview);
        myBackgroundColorButton.setOnChange(this::refreshPreview);
        myDotColorButton.setOnChange(this::refreshPreview);
        myIconPickerButton.setOnChange(this::refreshPreview);
        myFontStyleCombo.addActionListener(e -> refreshPreview());
        myFontSizeSpinner.addChangeListener(e -> refreshPreview());
        myStrikethroughCheck.addActionListener(e -> refreshPreview());
        myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                refreshPreview();
            }
        });
        refreshPreview();
    }

    /** Renders the label exactly as a matching row would appear in the panel. */
    private void refreshPreview() {
        Font current = myPreviewLabel.getFont();
        int baseSize = current != null ? current.getSize() : 13;
        String family = current != null ? current.getFamily() : Font.DIALOG;

        int styleIndex = myFontStyleCombo.getSelectedIndex();
        int swingStyle = switch (styleIndex) {
            case 1 -> Font.PLAIN;
            case 2 -> Font.BOLD;
            case 3 -> Font.ITALIC;
            case 4 -> Font.BOLD | Font.ITALIC;
            default -> current != null ? current.getStyle() : Font.PLAIN;
        };
        int sizeOverride = ((Number) myFontSizeSpinner.getValue()).intValue();
        Font baseFont = new Font(family, swingStyle, sizeOverride > 0 ? sizeOverride : baseSize);
        myPreviewLabel.setFont(myStrikethroughCheck.isSelected()
            ? baseFont.deriveFont(Map.of(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON))
            : baseFont);

        Color foreground = myFontColorButton.getColor();
        Color background = myBackgroundColorButton.getColor();
        myPreviewLabel.setForeground(
            foreground != null ? foreground : UIManager.getColor("Label.foreground"));
        myPreviewLabel.setBackground(
            background != null ? background : UIManager.getColor("Panel.background"));

        Image icon = RuleIcons.resolve(myIconPickerButton.getIconId());
        myPreviewLabel.setIcon(icon != null ? TargetAWT.to(icon) : null);
        myPreviewLabel.setText(myNameField.getText().isBlank() ? "Preview" : myNameField.getText());
    }

    // --- Loading and saving -------------------------------------------------------------------------

    private void loadFrom(@Nullable ColorRule rule) {
        if (rule == null) {
            myNameField.setText("");
            myPrioritySpinner.setValue(0);
            myActiveCheck.setSelected(true);
            myCombinatorCombo.setSelectedItem(Combinator.AND);
            myMatchers.clear();
            myIconPickerButton.setIconId("");
            myFontStyleCombo.setSelectedIndex(FONT_STYLE_DEFAULT_INDEX);
            myStrikethroughCheck.setSelected(false);
            return;
        }

        myNameField.setText(rule.name());
        myPrioritySpinner.setValue(rule.priority());
        myActiveCheck.setSelected(rule.active());
        myCombinatorCombo.setSelectedItem(rule.combinator());
        myMatchers.clear();
        myMatchers.addAll(rule.matchers());
        myMatcherModel.fireTableDataChanged();

        RuleStyle style = rule.style();
        myFontColorButton.setColor(PresetColors.decode(style.fontColor()));
        myBackgroundColorButton.setColor(PresetColors.decode(style.backgroundColor()));
        myDotColorButton.setColor(PresetColors.decode(style.iconDotColor()));
        myIconPickerButton.setIconId(style.iconId());
        myFontStyleCombo.setSelectedIndex(fontStyleIndexOf(style.fontStyle()));
        myFontSizeSpinner.setValue(style.fontSize() != null ? style.fontSize() : 0);
        myStrikethroughCheck.setSelected(Boolean.TRUE.equals(style.strikethrough()));
    }

    private static int fontStyleIndexOf(@Nullable Integer fontStyle) {
        if (fontStyle == null) {
            return FONT_STYLE_DEFAULT_INDEX;
        }
        return switch (fontStyle) {
            case Font.PLAIN -> 1;
            case Font.BOLD -> 2;
            case Font.ITALIC -> 3;
            case Font.BOLD | Font.ITALIC -> 4;
            default -> FONT_STYLE_DEFAULT_INDEX;
        };
    }

    private static @Nullable Integer fontStyleOfIndex(int index) {
        return switch (index) {
            case 1 -> Font.PLAIN;
            case 2 -> Font.BOLD;
            case 3 -> Font.ITALIC;
            case 4 -> Font.BOLD | Font.ITALIC;
            default -> null;
        };
    }

    @Override
    protected void doOKAction() {
        String name = myNameField.getText().trim();
        if (name.isEmpty()) {
            setErrorText("Name cannot be empty", myNameField);
            return;
        }
        if (myMatchers.isEmpty()) {
            // A rule with no matchers would never fire, so it is almost certainly a mistake.
            setErrorText("Add at least one matcher", myMatcherTable);
            return;
        }

        int fontSize = ((Number) myFontSizeSpinner.getValue()).intValue();
        Object combinator = myCombinatorCombo.getSelectedItem();
        myResult = new ColorRule(myId, name,
            ((Number) myPrioritySpinner.getValue()).intValue(),
            myActiveCheck.isSelected(),
            combinator instanceof Combinator value ? value : Combinator.AND,
            List.copyOf(myMatchers),
            new RuleStyle(
                myFontColorButton.getColorHex(),
                myBackgroundColorButton.getColorHex(),
                myDotColorButton.getColorHex(),
                fontStyleOfIndex(myFontStyleCombo.getSelectedIndex()),
                fontSize > 0 ? fontSize : null,
                myStrikethroughCheck.isSelected() ? Boolean.TRUE : null,
                myIconPickerButton.getIconId()));
        super.doOKAction();
    }

    // --- Matcher list --------------------------------------------------------------------------------

    private void addMatcher() {
        ColorMatcherEditDialog dialog = new ColorMatcherEditDialog(myProject, null);
        if (dialog.showAndGet() && dialog.getResult() != null) {
            myMatchers.add(dialog.getResult());
            myMatcherModel.fireTableDataChanged();
            myMatcherTable.setRowSelectionInterval(myMatchers.size() - 1, myMatchers.size() - 1);
        }
    }

    private void editSelectedMatcher() {
        int index = myMatcherTable.getSelectedRow();
        if (index < 0) {
            return;
        }
        ColorMatcherEditDialog dialog = new ColorMatcherEditDialog(myProject, myMatchers.get(index));
        if (dialog.showAndGet() && dialog.getResult() != null) {
            myMatchers.set(index, dialog.getResult());
            myMatcherModel.fireTableRowsUpdated(index, index);
        }
    }

    private void deleteSelectedMatcher() {
        int index = myMatcherTable.getSelectedRow();
        if (index >= 0) {
            myMatchers.remove(index);
            myMatcherModel.fireTableDataChanged();
        }
    }

    private final class MatcherTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return myMatchers.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Type" : "Summary";
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            RuleMatcher matcher = myMatchers.get(rowIndex);
            return columnIndex == 0 ? typeNameOf(matcher) : RuleMatcherDescriptions.describe(matcher);
        }
    }

    private static String typeNameOf(RuleMatcher matcher) {
        return switch (matcher) {
            case RuleMatcher.Size ignored -> "Size";
            case RuleMatcher.Name ignored -> "Name";
            case RuleMatcher.Contains ignored -> "Contains";
            case RuleMatcher.Date date -> date.field() == DateField.CREATED ? "Created" : "Modified";
            case RuleMatcher.Text text -> switch (text.field()) {
                case OWNER -> "Owner";
                case GROUP -> "Group";
                case PERMISSIONS -> "Permissions";
            };
            case RuleMatcher.Symlink ignored -> "Symlink";
            case RuleMatcher.NamedPipe ignored -> "Named pipe";
        };
    }

    private static final class CombinatorRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, @Nullable Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Component component =
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Combinator combinator) {
                setText(combinator == Combinator.AND
                    ? "All matchers must match (AND)"
                    : "Any matcher matches (OR)");
            }
            return component;
        }
    }
}
