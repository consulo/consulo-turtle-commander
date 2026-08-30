package consulo.turtlecommander.dialog;

import consulo.application.util.SystemInfo;
import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.operation.MultiRenameTemplate;
import consulo.turtlecommander.ui.TextFieldContextMenuUtil;
import consulo.turtlecommander.util.UiTask;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBScrollPane;
import consulo.ui.ex.awt.JBTextField;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.table.JBTable;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/** Batch rename with a live preview: the template is applied on every keystroke. */
public class MultiRenameDialog extends DialogWrapper {
    /** Only the entries whose name actually changes. */
    public record Rename(FileEntry entry, String newName) {
    }

    public enum CaseOption {
        UNCHANGED(MultiRenameTemplate.CaseMode.UNCHANGED, "Unchanged"),
        UPPER(MultiRenameTemplate.CaseMode.UPPER, "UPPER"),
        LOWER(MultiRenameTemplate.CaseMode.LOWER, "lower"),
        FIRST_UPPER(MultiRenameTemplate.CaseMode.FIRST_UPPER, "First capital"),
        EVERY_WORD_UPPER(MultiRenameTemplate.CaseMode.EVERY_WORD_UPPER, "Every Word Cap");

        private final MultiRenameTemplate.CaseMode myMode;
        private final String myLabel;

        CaseOption(MultiRenameTemplate.CaseMode mode, String label) {
            myMode = mode;
            myLabel = label;
        }

        public MultiRenameTemplate.CaseMode mode() {
            return myMode;
        }

        @Override
        public String toString() {
            return myLabel;
        }
    }

    private static final String HINT = "<html><small>"
        + "[N]=name &nbsp; [N1-5]=chars 1..5 &nbsp; [N3-]=from 3 to end &nbsp; [N2,3]=3 chars from pos 2 &nbsp; "
        + "[N-3]=3rd from end &nbsp; [E]=extension &nbsp; [C]=counter &nbsp; [Y][M][D][h][n][s]=date/time &nbsp; "
        + "[P]=parent dir &nbsp; [G]=grandparent dir &nbsp; "
        + "[R5]=5 random digits &nbsp; [Ra5]/[RA5]/[RM5]=lower/upper/mixed letters &nbsp; "
        + "Regex replace supports $1, $2, \u2026"
        + "</small></html>";

    private final List<FileEntry> myEntries;

    private final JBTextField myNameField = new JBTextField("[N]");
    private final JBTextField myExtField = new JBTextField("[E]");
    private final JBTextField mySearchField = new JBTextField("");
    private final JBTextField myReplaceField = new JBTextField("");
    private final JCheckBox myRegexCheck = new JCheckBox("Regex", false);
    private final JCheckBox myCaseSensitiveCheck = new JCheckBox("Case sensitive", true);
    private final JCheckBox myUseCurrentDateCheck =
        new JCheckBox("Use current date for [Y][M][D][h][n][s]", false);
    private final ComboBox<CaseOption> myCaseCombo = new ComboBox<>(CaseOption.values());
    private final JSpinner myCounterStartSpinner =
        new JSpinner(new SpinnerNumberModel(1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
    private final JSpinner myCounterStepSpinner =
        new JSpinner(new SpinnerNumberModel(1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
    private final JSpinner myCounterWidthSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));

    private final PreviewModel myTableModel = new PreviewModel();
    private final JBTable myTable = new JBTable(myTableModel);

    private Set<Integer> myConflictRows = Set.of();
    private List<String> myCurrentTargets = List.of();
    private List<Rename> myResult = List.of();

    /**
     * Names already present in the entries' parent directories, case-folded on Windows.
     * Snapshotted once on a pooled thread: the conflict preview recomputes on every keystroke, and
     * probing {@code Files.exists} per row per keystroke on the EDT lags badly on network shares.
     * Until the snapshot lands, targets are optimistically treated as free — the rename operation
     * itself still fails (and rolls back) on a real clash.
     */
    private volatile @Nullable Map<Path, Set<String>> myExistingNames;

    public MultiRenameDialog(Project project, List<FileEntry> entries) {
        super(project);
        myEntries = entries;
        myCaseCombo.setSelectedItem(CaseOption.UNCHANGED);

        setTitle("Multi-Rename");
        setOKButtonText("Rename");
        for (JBTextField field : templateFields()) {
            TextFieldContextMenuUtil.installStandardContextMenu(field);
        }
        wireLiveUpdate();
        init();
        recomputePreview();

        UiTask.backgroundThenUi(this::snapshotExistingNames, snapshot -> {
            myExistingNames = snapshot;
            recomputePreview();
        });
    }

    /** Populated in {@link #doOKAction()}. */
    public List<Rename> getResult() {
        return myResult;
    }

    private List<JBTextField> templateFields() {
        return List.of(myNameField, myExtField, mySearchField, myReplaceField);
    }

    private Map<Path, Set<String>> snapshotExistingNames() {
        Map<Path, Set<String>> snapshot = new HashMap<>();
        for (FileEntry entry : myEntries) {
            Path parent = entry.path().getParent();
            if (parent == null || snapshot.containsKey(parent)) {
                continue;
            }
            Set<String> names = new HashSet<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
                for (Path child : stream) {
                    Path fileName = child.getFileName();
                    if (fileName != null) {
                        names.add(foldName(fileName.toString()));
                    }
                }
            }
            catch (Exception e) {
                // Unreadable directory — treat every target in it as free.
            }
            snapshot.put(parent, names);
        }
        return snapshot;
    }

    private static String foldName(String name) {
        return SystemInfo.isWindows ? StringUtil.toLowerCase(name) : name;
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setPreferredSize(new Dimension(780, 520));
        root.add(buildControls(), BorderLayout.NORTH);
        root.add(buildPreview(), BorderLayout.CENTER);
        return root;
    }

    private JComponent buildControls() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(2, 4);
        gbc.gridy = 0;

        addRow(panel, gbc, "Name template:", myNameField, "Extension template:", myExtField);
        addRow(panel, gbc, "Search for:", mySearchField, "Replace with:", myReplaceField);

        JPanel flagsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        flagsPanel.add(myRegexCheck);
        flagsPanel.add(myCaseSensitiveCheck);
        flagsPanel.add(new JBLabel("   Case:"));
        flagsPanel.add(myCaseCombo);
        addFullWidth(panel, gbc, flagsPanel);

        JPanel counterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        counterPanel.add(new JBLabel("Counter \u2014 start:"));
        counterPanel.add(myCounterStartSpinner);
        counterPanel.add(new JBLabel("step:"));
        counterPanel.add(myCounterStepSpinner);
        counterPanel.add(new JBLabel("width:"));
        counterPanel.add(myCounterWidthSpinner);
        counterPanel.add(myUseCurrentDateCheck);
        addFullWidth(panel, gbc, counterPanel);

        addFullWidth(panel, gbc, new JBLabel(HINT));
        return panel;
    }

    private static void addRow(JPanel panel, GridBagConstraints gbc, String label, JComponent field,
                               String secondLabel, JComponent secondField) {
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        panel.add(new JBLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(field, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0.0;
        panel.add(new JBLabel(secondLabel), gbc);
        gbc.gridx = 3;
        gbc.weightx = 1.0;
        panel.add(secondField, gbc);
        gbc.gridy++;
    }

    private static void addFullWidth(JPanel panel, GridBagConstraints gbc, JComponent component) {
        gbc.gridx = 0;
        gbc.gridwidth = 4;
        gbc.weightx = 1.0;
        panel.add(component, gbc);
        gbc.gridwidth = 1;
        gbc.gridy++;
    }

    private JComponent buildPreview() {
        myTable.setShowGrid(true);
        myTable.setRowHeight(myTable.getRowHeight() + 2);
        myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        ConflictRenderer renderer = new ConflictRenderer(() -> myConflictRows);
        for (int col = 0; col < myTable.getColumnCount(); col++) {
            myTable.getColumnModel().getColumn(col).setCellRenderer(renderer);
        }

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JBLabel("Preview \u2014 conflicting rows are highlighted; rename is disabled while any "
            + "conflict exists."), BorderLayout.NORTH);
        panel.add(new JBScrollPane(myTable), BorderLayout.CENTER);
        return panel;
    }

    private void wireLiveUpdate() {
        DocumentListener docListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                recomputePreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                recomputePreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                recomputePreview();
            }
        };
        for (JBTextField field : templateFields()) {
            field.getDocument().addDocumentListener(docListener);
        }
        for (JCheckBox check : List.of(myRegexCheck, myCaseSensitiveCheck, myUseCurrentDateCheck)) {
            check.addActionListener(e -> recomputePreview());
        }
        myCaseCombo.addActionListener(e -> recomputePreview());
        for (JSpinner spinner : List.of(myCounterStartSpinner, myCounterStepSpinner, myCounterWidthSpinner)) {
            spinner.addChangeListener(e -> recomputePreview());
        }
    }

    private void recomputePreview() {
        Object selectedCase = myCaseCombo.getSelectedItem();
        MultiRenameTemplate.CaseMode caseMode = selectedCase instanceof CaseOption option
            ? option.mode()
            : MultiRenameTemplate.CaseMode.UNCHANGED;

        MultiRenameTemplate.Options options = new MultiRenameTemplate.Options(
            myNameField.getText(),
            myExtField.getText(),
            mySearchField.getText(),
            myReplaceField.getText(),
            myRegexCheck.isSelected(),
            myCaseSensitiveCheck.isSelected(),
            caseMode,
            new MultiRenameTemplate.CounterConfig(
                spinnerValue(myCounterStartSpinner),
                spinnerValue(myCounterStepSpinner),
                spinnerValue(myCounterWidthSpinner)),
            ZoneId.systemDefault(),
            myUseCurrentDateCheck.isSelected(),
            null,
            new Random());

        List<MultiRenameTemplate.Input> inputs = new ArrayList<>(myEntries.size());
        List<Path> sources = new ArrayList<>(myEntries.size());
        for (FileEntry entry : myEntries) {
            inputs.add(MultiRenameTemplate.Input.from(entry.path(), entry.lastModified()));
            sources.add(entry.path());
        }

        List<String> targets = MultiRenameTemplate.render(inputs, options);
        myConflictRows = MultiRenameTemplate.detectConflicts(sources, targets, SystemInfo.isWindows,
            this::existsOnDisk);
        myCurrentTargets = targets;
        myTableModel.refresh(myEntries, targets);
        UiTask.ui(this::updateOkState);
    }

    private boolean existsOnDisk(Path target) {
        Map<Path, Set<String>> names = myExistingNames;
        if (names == null) {
            return false;
        }
        Path fileName = target.getFileName();
        if (fileName == null) {
            return false;
        }
        Set<String> inDirectory = names.get(target.getParent());
        return inDirectory != null && inDirectory.contains(foldName(fileName.toString()));
    }

    private void updateOkState() {
        boolean anyChange = false;
        for (int i = 0; i < myEntries.size(); i++) {
            if (!myCurrentTargets.get(i).equals(myEntries.get(i).name())) {
                anyChange = true;
                break;
            }
        }
        setOKActionEnabled(myConflictRows.isEmpty() && anyChange);
    }

    @Override
    protected void doOKAction() {
        List<Rename> renames = new ArrayList<>();
        for (int i = 0; i < myEntries.size(); i++) {
            FileEntry entry = myEntries.get(i);
            String target = myCurrentTargets.get(i);
            if (!target.equals(entry.name())) {
                renames.add(new Rename(entry, target));
            }
        }
        myResult = renames;
        super.doOKAction();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myNameField;
    }

    private static int spinnerValue(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private static class PreviewModel extends AbstractTableModel {
        private List<FileEntry> mySources = List.of();
        private List<String> myTargets = List.of();

        void refresh(List<FileEntry> sources, List<String> targets) {
            mySources = sources;
            myTargets = targets;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return mySources.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Old name" : "New name";
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return columnIndex == 0 ? mySources.get(rowIndex).name() : myTargets.get(rowIndex);
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    private static class ConflictRenderer extends DefaultTableCellRenderer {
        private static final JBColor CONFLICT_BACKGROUND =
            new JBColor(new Color(0xFF, 0xD0, 0xD0), new Color(0x5A, 0x2C, 0x2C));
        private static final JBColor CONFLICT_FOREGROUND =
            new JBColor(new Color(0x80, 0x00, 0x00), new Color(0xFF, 0xC0, 0xC0));

        private final Supplier<Set<Integer>> myConflicts;

        ConflictRenderer(Supplier<Set<Integer>> conflicts) {
            myConflicts = conflicts;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, @Nullable Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component component =
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (isSelected) {
                return component;
            }
            boolean conflict = myConflicts.get().contains(row);
            component.setBackground(conflict ? CONFLICT_BACKGROUND : table.getBackground());
            component.setForeground(conflict ? CONFLICT_FOREGROUND : table.getForeground());
            return component;
        }
    }
}
