package consulo.turtlecommander.dialog;

import consulo.application.util.SystemInfo;
import consulo.project.Project;
import consulo.turtlecommander.search.DateFilter;
import consulo.turtlecommander.search.DateFilterMode;
import consulo.turtlecommander.search.FileSearchCriteria;
import consulo.turtlecommander.search.NamePatternMode;
import consulo.turtlecommander.search.PermissionsFilter;
import consulo.turtlecommander.search.PermissionsFilterMode;
import consulo.turtlecommander.search.ResultKind;
import consulo.turtlecommander.search.SizeFilter;
import consulo.turtlecommander.search.SizeFilterMode;
import consulo.turtlecommander.search.SymlinkSearchFilter;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.ui.TextFieldContextMenuUtil;
import consulo.turtlecommander.util.PermissionFlag;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBTextField;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.TextFieldWithStoredHistory;
import consulo.ui.ex.awt.ValidationInfo;
import org.jspecify.annotations.Nullable;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** The full search form: name, content, size, dates, ownership and permissions. */
public class FileSearchDialog extends DialogWrapper {
    private static final String CONTENT_HISTORY_KEY = "TurtleCommander.contentSearchHistory";
    private static final String DATE_HINT = "yyyy[-MM[-dd[ HH:mm]]]";
    private static final String DATE_ERROR =
        "Invalid date \u2014 use yyyy, yyyy-MM, yyyy-MM-dd, or yyyy-MM-dd HH:mm";

    /** Accepted date inputs, longest first; the calendar field is the granularity each implies. */
    private static final Map<String, Integer> DATE_FORMATS = new LinkedHashMap<>();

    static {
        DATE_FORMATS.put("yyyy-MM-dd HH:mm", Calendar.MINUTE);
        DATE_FORMATS.put("yyyy-MM-dd", Calendar.DAY_OF_MONTH);
        DATE_FORMATS.put("yyyy-MM", Calendar.MONTH);
        DATE_FORMATS.put("yyyy", Calendar.YEAR);
    }

    private final boolean myHostWindows = SystemInfo.isWindows;
    private final List<PermissionFlag> myPermissionFlagsForOs =
        myHostWindows ? PermissionFlag.DOS_FLAGS : PermissionFlag.POSIX_FLAGS;

    private final JBTextField myRootField;

    private final JCheckBox myNameCheckBox;
    private final ComboBox<String> myNameCombo = new ComboBox<>();
    private final JRadioButton myGlobRadio;
    private final JRadioButton myRegexpRadio;
    private final JCheckBox myCaseSensitiveCheckBox;
    private final JRadioButton myResultKindAllRadio;
    private final JRadioButton myResultKindFilesRadio;
    private final JRadioButton myResultKindDirsRadio;
    private final ComboBox<String> mySymlinkFilterCombo =
        new ComboBox<>(new String[]{"Any", "Only symlinks", "Exclude symlinks"});

    private final JCheckBox myContentCheckBox;
    private final TextFieldWithStoredHistory myContentField = new TextFieldWithStoredHistory(CONTENT_HISTORY_KEY);
    private final JCheckBox myContentCaseSensitiveCheckBox;

    private final JCheckBox mySizeCheckBox;
    private final ComboBox<String> mySizeModeCombo = new ComboBox<>(
        new DefaultComboBoxModel<>(new String[]{"More than", "Approximately equal", "Less than", "In between"}));
    private final JBTextField mySizeField1 = new JBTextField(10);
    private final JBTextField mySizeField2 = new JBTextField(10);
    private final ComboBox<String> mySizeUnitCombo =
        new ComboBox<>(new DefaultComboBoxModel<>(new String[]{"B", "KB", "MB", "GB"}));
    private final JBLabel mySizeField2Label = new JBLabel("and");

    private final JCheckBox myCreationDateCheckBox;
    private final ComboBox<String> myCreationDateModeCombo = newDateModeCombo();
    private final JBTextField myCreationDateField1 = newDateField();
    private final JBTextField myCreationDateField2 = newDateField();
    private final JBLabel myCreationDateField2Label = new JBLabel("and");

    private final JCheckBox myModificationDateCheckBox;
    private final ComboBox<String> myModificationDateModeCombo = newDateModeCombo();
    private final JBTextField myModificationDateField1 = newDateField();
    private final JBTextField myModificationDateField2 = newDateField();
    private final JBLabel myModificationDateField2Label = new JBLabel("and");

    private final JCheckBox myOwnerCheckBox;
    private final JBTextField myOwnerField;

    private final JCheckBox myGroupCheckBox;
    private final JBTextField myGroupField;

    private final JCheckBox myPermissionsCheckBox;
    private final ComboBox<String> myPermissionsModeCombo = new ComboBox<>(
        new DefaultComboBoxModel<>(new String[]{"Exact match", "All of", "Any of", "None of"}));
    private final Map<PermissionFlag, JCheckBox> myPermissionFlagCheckBoxes = new LinkedHashMap<>();

    public FileSearchDialog(Project project, Path initialRoot, @Nullable FileSearchCriteria initialCriteria) {
        super(project);
        myRootField = new JBTextField(initialRoot.toString());

        boolean regexp = initialCriteria != null && initialCriteria.namePatternMode() == NamePatternMode.REGEXP;
        myNameCheckBox = new JCheckBox("Search by name",
            initialCriteria == null || initialCriteria.namePattern() != null);
        myNameCombo.setEditable(true);
        String initialPattern = initialCriteria != null && initialCriteria.namePattern() != null
            ? initialCriteria.namePattern()
            : defaultPattern(regexp);
        setNameComboItems(initialPattern, TurtleCommanderSettings.getInstance().getNameSearchHistory(regexp),
            regexp);

        myGlobRadio = new JRadioButton("Glob", !regexp);
        myRegexpRadio = new JRadioButton("Regexp", regexp);
        myCaseSensitiveCheckBox = new JCheckBox("Case sensitive",
            initialCriteria != null && initialCriteria.caseSensitive());

        ResultKind resultKind = initialCriteria != null ? initialCriteria.resultKind() : null;
        myResultKindAllRadio = new JRadioButton("All", resultKind != ResultKind.FILES && resultKind != ResultKind.DIRS);
        myResultKindFilesRadio = new JRadioButton("Files", resultKind == ResultKind.FILES);
        myResultKindDirsRadio = new JRadioButton("Dirs", resultKind == ResultKind.DIRS);
        mySymlinkFilterCombo.setSelectedIndex(symlinkIndex(initialCriteria));

        myContentCheckBox = new JCheckBox("Search by content",
            initialCriteria != null && initialCriteria.contentPattern() != null);
        myContentField.setHistorySize(10);
        myContentField.setText(initialCriteria != null && initialCriteria.contentPattern() != null
            ? initialCriteria.contentPattern() : "");
        myContentCaseSensitiveCheckBox = new JCheckBox("Case sensitive",
            initialCriteria != null && initialCriteria.contentCaseSensitive());

        SizeFilter sizeFilter = initialCriteria != null ? initialCriteria.sizeFilter() : null;
        mySizeCheckBox = new JCheckBox("Search by size", sizeFilter != null);
        if (sizeFilter != null) {
            mySizeModeCombo.setSelectedIndex(sizeFilter.mode().ordinal());
            mySizeField1.setText(bytesToUnitValue(sizeFilter.sizeBytes()));
            if (sizeFilter.sizeBytes2() != null) {
                mySizeField2.setText(bytesToUnitValue(sizeFilter.sizeBytes2()));
            }
            mySizeUnitCombo.setSelectedItem(bytesToUnitName(sizeFilter.sizeBytes()));
        }
        else {
            mySizeUnitCombo.setSelectedItem("MB");
        }

        DateFilter creation = initialCriteria != null ? initialCriteria.creationDateFilter() : null;
        myCreationDateCheckBox = new JCheckBox("Search by creation date", creation != null);
        applyDateFilter(creation, myCreationDateModeCombo, myCreationDateField1, myCreationDateField2);

        DateFilter modification = initialCriteria != null ? initialCriteria.modificationDateFilter() : null;
        myModificationDateCheckBox = new JCheckBox("Search by modification date", modification != null);
        applyDateFilter(modification, myModificationDateModeCombo, myModificationDateField1,
            myModificationDateField2);

        String ownerPattern = initialCriteria != null ? initialCriteria.ownerPattern() : null;
        myOwnerCheckBox = new JCheckBox("Search by owner", ownerPattern != null);
        myOwnerField = new JBTextField(ownerPattern != null ? ownerPattern : "");

        String groupPattern = initialCriteria != null ? initialCriteria.groupPattern() : null;
        myGroupCheckBox = new JCheckBox("Search by group", groupPattern != null);
        myGroupField = new JBTextField(groupPattern != null ? groupPattern : "");

        PermissionsFilter permissions = initialCriteria != null ? initialCriteria.permissionsFilter() : null;
        myPermissionsCheckBox = new JCheckBox("Search by permissions", permissions != null);
        if (permissions != null) {
            myPermissionsModeCombo.setSelectedIndex(permissions.mode().ordinal());
        }
        for (PermissionFlag flag : myPermissionFlagsForOs) {
            boolean selected = permissions != null && permissions.flags().contains(flag);
            myPermissionFlagCheckBoxes.put(flag, new JCheckBox(permissionLabel(flag), selected));
        }

        setTitle("Search Files");
        setOKButtonText("Search");

        for (JBTextField field : List.of(myRootField, mySizeField1, mySizeField2,
            myCreationDateField1, myCreationDateField2,
            myModificationDateField1, myModificationDateField2,
            myOwnerField, myGroupField)) {
            TextFieldContextMenuUtil.installStandardContextMenu(field);
        }
        TextFieldContextMenuUtil.installStandardContextMenu(nameEditor());
        TextFieldContextMenuUtil.installStandardContextMenu(myContentField.getTextEditor());

        init();

        setupEnabling();
        setupSizeInBetween();
        setupDateInBetween();
        setupNameHistorySwap();
    }

    /** Splits a byte count into the largest unit that keeps it above 1, for round-tripping the form. */
    public static String bytesToUnitValue(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format("%.1f", bytes / (1024.0 * 1024 * 1024));
        }
        if (bytes >= 1024L * 1024) {
            return String.format("%.1f", bytes / (1024.0 * 1024));
        }
        if (bytes >= 1024L) {
            return String.format("%.1f", bytes / 1024.0);
        }
        return String.valueOf(bytes);
    }

    public static String bytesToUnitName(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return "GB";
        }
        if (bytes >= 1024L * 1024) {
            return "MB";
        }
        if (bytes >= 1024L) {
            return "KB";
        }
        return "B";
    }

    /**
     * Expands a partial date into the window it denotes: "2024" covers the whole year, "2024-03"
     * the whole month, and so on. Returns {@code null} when nothing parses cleanly, which is what
     * validation reports on.
     */
    static long @Nullable [] parseDateRange(String text) {
        if (text.isBlank()) {
            return null;
        }
        for (Map.Entry<String, Integer> format : DATE_FORMATS.entrySet()) {
            SimpleDateFormat formatter = new SimpleDateFormat(format.getKey());
            formatter.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = formatter.parse(text, position);
            if (parsed == null || position.getIndex() != text.length()) {
                continue;
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(parsed);
            long start = calendar.getTimeInMillis();
            calendar.add(format.getValue(), 1);
            calendar.add(Calendar.MILLISECOND, -1);
            return new long[]{start, calendar.getTimeInMillis()};
        }
        return null;
    }

    private static ComboBox<String> newDateModeCombo() {
        return new ComboBox<>(new DefaultComboBoxModel<>(
            new String[]{"Earlier than", "Approximately equal", "Later than", "In between"}));
    }

    private static JBTextField newDateField() {
        JBTextField field = new JBTextField(16);
        field.getEmptyText().setText(DATE_HINT);
        return field;
    }

    private static void applyDateFilter(@Nullable DateFilter filter, ComboBox<String> modeCombo,
                                        JBTextField field1, JBTextField field2) {
        if (filter == null) {
            return;
        }
        modeCombo.setSelectedIndex(filter.mode().ordinal());
        field1.setText(filter.text1());
        if (filter.text2() != null) {
            field2.setText(filter.text2());
        }
    }

    private static int symlinkIndex(@Nullable FileSearchCriteria criteria) {
        if (criteria == null) {
            return 0;
        }
        return switch (criteria.symlinkFilter()) {
            case ONLY -> 1;
            case EXCLUDE -> 2;
            case ANY -> 0;
        };
    }

    private static String defaultPattern(boolean regexp) {
        return regexp ? ".*" : "*";
    }

    private JTextField nameEditor() {
        return (JTextField) myNameCombo.getEditor().getEditorComponent();
    }

    private String nameText() {
        Object item = myNameCombo.getEditor().getItem();
        return item instanceof String text ? text.trim() : "";
    }

    private String contentText() {
        String text = myContentField.getText();
        return text != null ? text.trim() : "";
    }

    private void setNameComboItems(String selected, List<String> history, boolean regexp) {
        Set<String> items = new LinkedHashSet<>();
        if (!selected.isEmpty()) {
            items.add(selected);
        }
        items.addAll(history);
        if (items.isEmpty()) {
            items.add(defaultPattern(regexp));
        }
        myNameCombo.setModel(new DefaultComboBoxModel<>(items.toArray(new String[0])));
        // Restore the editor text rather than letting the new model's first item win.
        myNameCombo.setSelectedItem(selected.isEmpty() ? items.iterator().next() : selected);
    }

    /**
     * Glob and regex patterns live in separate MRU histories, so when the user toggles the
     * Glob/Regexp radio we rebuild the name dropdown from the matching history. The currently typed
     * text is preserved so in-progress edits survive realising the wrong mode was picked.
     */
    private void setupNameHistorySwap() {
        Runnable swap = () -> {
            boolean regexp = myRegexpRadio.isSelected();
            setNameComboItems(nameText(),
                TurtleCommanderSettings.getInstance().getNameSearchHistory(regexp), regexp);
        };
        for (JRadioButton radio : List.of(myGlobRadio, myRegexpRadio)) {
            radio.addActionListener(e -> {
                swap.run();
                nameEditor().requestFocusInWindow();
            });
        }
        for (AbstractButton button : List.of(myCaseSensitiveCheckBox, myResultKindAllRadio,
            myResultKindFilesRadio, myResultKindDirsRadio)) {
            button.addActionListener(e -> nameEditor().requestFocusInWindow());
        }
    }

    private void setupEnabling() {
        bindSection(myNameCheckBox, this::updateNameEnabled, this::nameEditor);
        bindSection(myContentCheckBox, this::updateContentEnabled, myContentField::getTextEditor);
        myContentCaseSensitiveCheckBox.addActionListener(
            e -> myContentField.getTextEditor().requestFocusInWindow());
        bindSection(mySizeCheckBox, this::updateSizeEnabled, () -> mySizeField1);
        bindSection(myCreationDateCheckBox, this::updateCreationDateEnabled, () -> myCreationDateField1);
        bindSection(myModificationDateCheckBox, this::updateModificationDateEnabled,
            () -> myModificationDateField1);
        bindSection(myOwnerCheckBox, () -> myOwnerField.setEnabled(myOwnerCheckBox.isSelected()),
            () -> myOwnerField);
        bindSection(myGroupCheckBox, () -> myGroupField.setEnabled(myGroupCheckBox.isSelected()),
            () -> myGroupField);
        myPermissionsCheckBox.addActionListener(e -> updatePermissionsEnabled());
        updatePermissionsEnabled();
    }

    /** Wires a section checkbox to its enable/disable pass and moves focus into it when ticked. */
    private static void bindSection(JCheckBox checkBox, Runnable updateEnabled,
                                    java.util.function.Supplier<JComponent> focusTarget) {
        checkBox.addActionListener(e -> {
            updateEnabled.run();
            if (checkBox.isSelected()) {
                focusTarget.get().requestFocusInWindow();
            }
        });
        updateEnabled.run();
    }

    private void updateNameEnabled() {
        boolean enabled = myNameCheckBox.isSelected();
        myNameCombo.setEnabled(enabled);
        myGlobRadio.setEnabled(enabled);
        myRegexpRadio.setEnabled(enabled);
        myCaseSensitiveCheckBox.setEnabled(enabled);
        myResultKindAllRadio.setEnabled(enabled);
        myResultKindFilesRadio.setEnabled(enabled);
        myResultKindDirsRadio.setEnabled(enabled);
    }

    private void updateContentEnabled() {
        boolean enabled = myContentCheckBox.isSelected();
        myContentField.setEnabled(enabled);
        myContentCaseSensitiveCheckBox.setEnabled(enabled);
    }

    private void updateSizeEnabled() {
        boolean enabled = mySizeCheckBox.isSelected();
        mySizeModeCombo.setEnabled(enabled);
        mySizeField1.setEnabled(enabled);
        mySizeField2.setEnabled(enabled);
        mySizeUnitCombo.setEnabled(enabled);
    }

    private void updateCreationDateEnabled() {
        boolean enabled = myCreationDateCheckBox.isSelected();
        myCreationDateModeCombo.setEnabled(enabled);
        myCreationDateField1.setEnabled(enabled);
        myCreationDateField2.setEnabled(enabled);
    }

    private void updateModificationDateEnabled() {
        boolean enabled = myModificationDateCheckBox.isSelected();
        myModificationDateModeCombo.setEnabled(enabled);
        myModificationDateField1.setEnabled(enabled);
        myModificationDateField2.setEnabled(enabled);
    }

    private void updatePermissionsEnabled() {
        boolean enabled = myPermissionsCheckBox.isSelected();
        myPermissionsModeCombo.setEnabled(enabled);
        for (JCheckBox check : myPermissionFlagCheckBoxes.values()) {
            check.setEnabled(enabled);
        }
    }

    private void setupSizeInBetween() {
        Runnable updateVisibility = () -> {
            boolean inBetween = mySizeModeCombo.getSelectedIndex() == SizeFilterMode.IN_BETWEEN.ordinal();
            mySizeField2.setVisible(inBetween);
            mySizeField2Label.setVisible(inBetween);
        };
        // Picking a mode is a strong signal the user wants to type a bound now, so move focus to
        // the first value field (the second only shows for "In between").
        mySizeModeCombo.addActionListener(e -> {
            updateVisibility.run();
            mySizeField1.requestFocusInWindow();
        });
        // Picking a unit likewise sends focus back to the value field for immediate typing.
        mySizeUnitCombo.addActionListener(e -> mySizeField1.requestFocusInWindow());
        updateVisibility.run();
    }

    private void setupDateInBetween() {
        bindDateInBetween(myCreationDateModeCombo, myCreationDateField1, myCreationDateField2,
            myCreationDateField2Label);
        bindDateInBetween(myModificationDateModeCombo, myModificationDateField1, myModificationDateField2,
            myModificationDateField2Label);
    }

    private static void bindDateInBetween(ComboBox<String> modeCombo, JBTextField field1, JBTextField field2,
                                          JBLabel field2Label) {
        Runnable update = () -> {
            boolean inBetween = modeCombo.getSelectedIndex() == DateFilterMode.IN_BETWEEN.ordinal();
            field2.setVisible(inBetween);
            field2Label.setVisible(inBetween);
        };
        modeCombo.addActionListener(e -> {
            update.run();
            field1.requestFocusInWindow();
        });
        update.run();
    }

    @Override
    protected JComponent createCenterPanel() {
        // POSIX permissions add an Owner/Group/Others legend row plus a setuid/setgid/sticky row
        // under the rwx checkboxes, so the dialog needs more vertical room than on Windows.
        int extraHeight = myHostWindows ? 0 : 50;
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMinimumSize(new Dimension(700, 440 + extraHeight));
        panel.setPreferredSize(new Dimension(700, 590 + extraHeight));

        panel.add(leftAligned(new JBLabel("Search in:")));
        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        rootPanel.add(myRootField, BorderLayout.CENTER);
        rootPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, myRootField.getPreferredSize().height + 8));
        panel.add(leftAligned(rootPanel));

        panel.add(leftAligned(myNameCheckBox));
        panel.add(createNamePanel());
        panel.add(Box.createVerticalStrut(8));

        panel.add(leftAligned(myContentCheckBox));
        panel.add(createContentPanel());
        panel.add(Box.createVerticalStrut(8));

        panel.add(leftAligned(mySizeCheckBox));
        panel.add(createSizePanel());
        panel.add(Box.createVerticalStrut(8));

        panel.add(leftAligned(myCreationDateCheckBox));
        panel.add(createDatePanel(myCreationDateModeCombo, myCreationDateField1, myCreationDateField2,
            myCreationDateField2Label));
        panel.add(Box.createVerticalStrut(8));

        panel.add(leftAligned(myModificationDateCheckBox));
        panel.add(createDatePanel(myModificationDateModeCombo, myModificationDateField1,
            myModificationDateField2, myModificationDateField2Label));
        panel.add(Box.createVerticalStrut(8));

        panel.add(createOwnerGroupPanel());
        panel.add(Box.createVerticalStrut(8));

        panel.add(leftAligned(myPermissionsCheckBox));
        panel.add(createPermissionsPanel());
        return panel;
    }

    /**
     * Owner and group share one row. The section checkbox sits flush with the other section
     * checkboxes (name, size, dates, permissions) by zeroing the horizontal inset on the first
     * column; the fields and the group checkbox still get padded.
     */
    private JPanel createOwnerGroupPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = JBUI.insets(2, 0, 2, 4);
        panel.add(myOwnerCheckBox, gbc);

        gbc.insets = JBUI.insets(2, 4);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        if (myHostWindows) {
            gbc.gridwidth = 3;
        }
        panel.add(myOwnerField, gbc);

        if (!myHostWindows) {
            gbc.gridx = 2;
            gbc.fill = GridBagConstraints.NONE;
            gbc.weightx = 0.0;
            panel.add(myGroupCheckBox, gbc);
            gbc.gridx = 3;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            panel.add(myGroupField, gbc);
        }
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return leftAligned(panel);
    }

    private JPanel createPermissionsPanel() {
        JPanel panel = indentedSection();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = JBUI.insets(2, 4);
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(myPermissionsModeCombo, gbc);

        // DOS: 4 flags in one row. POSIX: 9 rwx flags in one row with short r/w/x labels and an
        // Owner/Group/Others legend underneath each triplet, then a third row with the
        // setuid/setgid/sticky special bits aligned under the group they belong to.
        if (myHostWindows) {
            int col = 1;
            for (PermissionFlag flag : myPermissionFlagsForOs) {
                gbc.gridx = col++;
                panel.add(myPermissionFlagCheckBoxes.get(flag), gbc);
            }
        }
        else {
            gbc.gridx = 1;
            panel.add(createPosixFlagsPanel(), gbc);
        }
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    /** One group of permission bits: the rwx triplet, its legend, and the special bit under it. */
    private record PermGroup(String legend, PermissionFlag read, PermissionFlag write,
                             PermissionFlag execute, PermissionFlag special) {
    }

    private JPanel createPosixFlagsPanel() {
        // Each group carries its special bit so it lands under the same column block: setuid under
        // Owner, setgid under Group, sticky under Others.
        List<PermGroup> groups = List.of(
            new PermGroup("Owner", PermissionFlag.OWNER_READ, PermissionFlag.OWNER_WRITE,
                PermissionFlag.OWNER_EXECUTE, PermissionFlag.SETUID),
            new PermGroup("Group", PermissionFlag.GROUP_READ, PermissionFlag.GROUP_WRITE,
                PermissionFlag.GROUP_EXECUTE, PermissionFlag.SETGID),
            new PermGroup("Others", PermissionFlag.OTHERS_READ, PermissionFlag.OTHERS_WRITE,
                PermissionFlag.OTHERS_EXECUTE, PermissionFlag.STICKY));

        JPanel flagsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = JBUI.insets(2, 4);

        for (int groupIdx = 0; groupIdx < groups.size(); groupIdx++) {
            PermGroup group = groups.get(groupIdx);
            int baseCol = groupIdx * 3;

            gbc.gridy = 0;
            gbc.gridwidth = 1;
            gbc.fill = GridBagConstraints.NONE;
            gbc.gridx = baseCol;
            flagsPanel.add(myPermissionFlagCheckBoxes.get(group.read()), gbc);
            gbc.gridx = baseCol + 1;
            flagsPanel.add(myPermissionFlagCheckBoxes.get(group.write()), gbc);
            gbc.gridx = baseCol + 2;
            flagsPanel.add(myPermissionFlagCheckBoxes.get(group.execute()), gbc);

            gbc.gridy = 1;
            gbc.gridx = baseCol;
            gbc.gridwidth = 3;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            flagsPanel.add(new JBLabel(group.legend(), SwingConstants.CENTER), gbc);

            gbc.gridy = 2;
            gbc.gridx = baseCol;
            gbc.gridwidth = 3;
            gbc.fill = GridBagConstraints.NONE;
            flagsPanel.add(myPermissionFlagCheckBoxes.get(group.special()), gbc);
        }
        return flagsPanel;
    }

    private static String permissionLabel(PermissionFlag flag) {
        return switch (flag) {
            case OWNER_READ, GROUP_READ, OTHERS_READ -> "r";
            case OWNER_WRITE, GROUP_WRITE, OTHERS_WRITE -> "w";
            case OWNER_EXECUTE, GROUP_EXECUTE, OTHERS_EXECUTE -> "x";
            case SETUID -> "setuid";
            case SETGID -> "setgid";
            case STICKY -> "sticky";
            case DOS_READ_ONLY -> "Read-only";
            case DOS_HIDDEN -> "Hidden";
            case DOS_SYSTEM -> "System";
            case DOS_ARCHIVE -> "Archive";
        };
    }

    private JPanel createNamePanel() {
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(myGlobRadio);
        modeGroup.add(myRegexpRadio);

        ButtonGroup resultKindGroup = new ButtonGroup();
        resultKindGroup.add(myResultKindAllRadio);
        resultKindGroup.add(myResultKindFilesRadio);
        resultKindGroup.add(myResultKindDirsRadio);

        JPanel resultKindPanel = new JPanel();
        resultKindPanel.setLayout(new BoxLayout(resultKindPanel, BoxLayout.X_AXIS));
        resultKindPanel.setOpaque(false);
        resultKindPanel.add(myResultKindAllRadio);
        resultKindPanel.add(myResultKindFilesRadio);
        resultKindPanel.add(myResultKindDirsRadio);
        resultKindPanel.add(Box.createHorizontalStrut(12));
        resultKindPanel.add(new JLabel("Symlinks:"));
        resultKindPanel.add(Box.createHorizontalStrut(4));
        resultKindPanel.add(mySymlinkFilterCombo);

        JPanel panel = indentedSection();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = JBUI.insets(2, 4);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridwidth = 4;
        panel.add(myNameCombo, gbc);

        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(myGlobRadio, gbc);
        gbc.gridx = 1;
        panel.add(myRegexpRadio, gbc);
        gbc.gridx = 2;
        panel.add(myCaseSensitiveCheckBox, gbc);

        // Push the All/Files/Dirs radio group to the right edge of the row. fill=NONE keeps the
        // panel at its preferred width so anchor=EAST has room to push against.
        gbc.gridx = 3;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(resultKindPanel, gbc);

        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    private JPanel createContentPanel() {
        JPanel panel = indentedSection();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = JBUI.insets(2, 4);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(myContentField, gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        panel.add(myContentCaseSensitiveCheckBox, gbc);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    private JPanel createSizePanel() {
        JPanel panel = indentedSection();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = JBUI.insets(2, 4);
        gbc.gridy = 0;
        gbc.gridx = 0;
        panel.add(mySizeModeCombo, gbc);
        gbc.gridx = 1;
        panel.add(mySizeField1, gbc);
        gbc.gridx = 2;
        panel.add(mySizeField2Label, gbc);
        gbc.gridx = 3;
        panel.add(mySizeField2, gbc);
        gbc.gridx = 4;
        panel.add(mySizeUnitCombo, gbc);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    private static JPanel createDatePanel(ComboBox<String> modeCombo, JBTextField field1, JBTextField field2,
                                          JBLabel field2Label) {
        JPanel panel = indentedSection();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = JBUI.insets(2, 4);
        gbc.gridy = 0;
        gbc.gridx = 0;
        panel.add(modeCombo, gbc);
        gbc.gridx = 1;
        panel.add(field1, gbc);
        gbc.gridx = 2;
        panel.add(field2Label, gbc);
        gbc.gridx = 3;
        panel.add(field2, gbc);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    private static JPanel indentedSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(2, 20, 0, 0));
        return panel;
    }

    /**
     * Validates the form before the dialog closes. A non-null return keeps the dialog open,
     * highlights the returned component with a tooltip, and focuses it — so an invalid glob/regex
     * is caught immediately instead of surfacing later in the search results tab, by which time the
     * input form is already gone.
     */
    @Override
    protected @Nullable ValidationInfo doValidate() {
        ValidationInfo root = validateRoot();
        if (root != null) {
            return root;
        }
        ValidationInfo name = validateName();
        if (name != null) {
            return name;
        }
        ValidationInfo size = validateSize();
        if (size != null) {
            return size;
        }
        return validateDates();
    }

    /** The search root must be a syntactically valid path — otherwise {@code Path.of} throws later. */
    private @Nullable ValidationInfo validateRoot() {
        String text = myRootField.getText().trim();
        if (text.isEmpty()) {
            return new ValidationInfo("Enter a folder to search in", myRootField);
        }
        try {
            Path.of(text);
            return null;
        }
        catch (InvalidPathException e) {
            return new ValidationInfo("Invalid folder path: " + (e.getReason() != null ? e.getReason() : text),
                myRootField);
        }
    }

    private @Nullable ValidationInfo validateName() {
        if (!myNameCheckBox.isSelected()) {
            return null;
        }
        String pattern = nameText();
        if (pattern.isBlank()) {
            return null;
        }
        try {
            if (myRegexpRadio.isSelected()) {
                Pattern.compile(pattern);
            }
            else {
                FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            }
            return null;
        }
        catch (Exception e) {
            String kind = myRegexpRadio.isSelected() ? "regex" : "glob";
            return new ValidationInfo("Invalid " + kind + ": " + (e.getMessage() != null ? e.getMessage() : pattern),
                nameEditor());
        }
    }

    /** A non-numeric size would silently become 0 ("more than 0 B" matches everything). */
    private @Nullable ValidationInfo validateSize() {
        if (!mySizeCheckBox.isSelected()) {
            return null;
        }
        ValidationInfo first = validateSizeField(mySizeField1);
        if (first != null) {
            return first;
        }
        if (mySizeModeCombo.getSelectedIndex() == SizeFilterMode.IN_BETWEEN.ordinal()) {
            return validateSizeField(mySizeField2);
        }
        return null;
    }

    private static @Nullable ValidationInfo validateSizeField(JBTextField field) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return new ValidationInfo("Enter a size", field);
        }
        Double value = parseDouble(text);
        if (value == null) {
            return new ValidationInfo("Size must be a number", field);
        }
        if (value < 0) {
            return new ValidationInfo("Size cannot be negative", field);
        }
        return null;
    }

    /** An unparseable date would be dropped silently, running the search with no date filter. */
    private @Nullable ValidationInfo validateDates() {
        ValidationInfo creation = validateDateField(myCreationDateCheckBox, myCreationDateModeCombo,
            myCreationDateField1, myCreationDateField2);
        if (creation != null) {
            return creation;
        }
        return validateDateField(myModificationDateCheckBox, myModificationDateModeCombo,
            myModificationDateField1, myModificationDateField2);
    }

    private static @Nullable ValidationInfo validateDateField(JCheckBox checkBox, ComboBox<String> modeCombo,
                                                              JBTextField field1, JBTextField field2) {
        if (!checkBox.isSelected()) {
            return null;
        }
        if (parseDateRange(field1.getText().trim()) == null) {
            return new ValidationInfo(DATE_ERROR, field1);
        }
        if (modeCombo.getSelectedIndex() == DateFilterMode.IN_BETWEEN.ordinal()
            && parseDateRange(field2.getText().trim()) == null) {
            return new ValidationInfo(DATE_ERROR, field2);
        }
        return null;
    }

    @Override
    protected void doOKAction() {
        // Only persist to the MRU history once validation has passed, so invalid patterns the user
        // fat-fingered never leak into the history dropdown. Glob and regex have separate histories
        // so the dropdown always matches the selected mode.
        if (myNameCheckBox.isSelected() && !nameText().isBlank()) {
            TurtleCommanderSettings.getInstance().addNameSearchHistory(nameText(), myRegexpRadio.isSelected());
        }
        if (myContentCheckBox.isSelected() && !contentText().isBlank()) {
            myContentField.addCurrentTextToHistory();
        }
        super.doOKAction();
    }

    public FileSearchCriteria getCriteria() {
        String namePattern = myNameCheckBox.isSelected() && !nameText().isBlank() ? nameText() : null;
        NamePatternMode nameMode = myRegexpRadio.isSelected() ? NamePatternMode.REGEXP : NamePatternMode.GLOB;
        String contentPattern = myContentCheckBox.isSelected() && !contentText().isBlank() ? contentText() : null;

        String owner = myOwnerCheckBox.isSelected() && !myOwnerField.getText().isBlank()
            ? myOwnerField.getText().trim() : null;
        String group = !myHostWindows && myGroupCheckBox.isSelected() && !myGroupField.getText().isBlank()
            ? myGroupField.getText().trim() : null;

        ResultKind resultKind = myResultKindFilesRadio.isSelected() ? ResultKind.FILES
            : myResultKindDirsRadio.isSelected() ? ResultKind.DIRS
            : ResultKind.ALL;

        SymlinkSearchFilter symlinkFilter = switch (mySymlinkFilterCombo.getSelectedIndex()) {
            case 1 -> SymlinkSearchFilter.ONLY;
            case 2 -> SymlinkSearchFilter.EXCLUDE;
            default -> SymlinkSearchFilter.ANY;
        };

        return new FileSearchCriteria(
            Path.of(myRootField.getText().trim()),
            namePattern,
            nameMode,
            myCaseSensitiveCheckBox.isSelected(),
            resultKind,
            contentPattern,
            myContentCaseSensitiveCheckBox.isSelected(),
            buildSizeFilter(),
            parseDateFilter(myCreationDateCheckBox, myCreationDateModeCombo, myCreationDateField1,
                myCreationDateField2),
            parseDateFilter(myModificationDateCheckBox, myModificationDateModeCombo, myModificationDateField1,
                myModificationDateField2),
            owner,
            group,
            buildPermissionsFilter(),
            symlinkFilter);
    }

    private @Nullable SizeFilter buildSizeFilter() {
        if (!mySizeCheckBox.isSelected()) {
            return null;
        }
        SizeFilterMode mode = SizeFilterMode.values()[mySizeModeCombo.getSelectedIndex()];
        long multiplier = switch (String.valueOf(mySizeUnitCombo.getSelectedItem())) {
            case "KB" -> 1024L;
            case "MB" -> 1024L * 1024;
            case "GB" -> 1024L * 1024 * 1024;
            default -> 1L;
        };
        long size1 = sizeFieldBytes(mySizeField1, multiplier);
        Long size2 = mode == SizeFilterMode.IN_BETWEEN ? sizeFieldBytes(mySizeField2, multiplier) : null;
        return new SizeFilter(mode, size1, size2);
    }

    private static long sizeFieldBytes(JBTextField field, long multiplier) {
        Double value = parseDouble(field.getText().trim());
        return (value != null ? value.longValue() : 0L) * multiplier;
    }

    private @Nullable PermissionsFilter buildPermissionsFilter() {
        if (!myPermissionsCheckBox.isSelected()) {
            return null;
        }
        PermissionsFilterMode mode = PermissionsFilterMode.values()[myPermissionsModeCombo.getSelectedIndex()];
        Set<PermissionFlag> selected = new LinkedHashSet<>();
        for (Map.Entry<PermissionFlag, JCheckBox> entry : myPermissionFlagCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        return new PermissionsFilter(mode, selected);
    }

    private static @Nullable DateFilter parseDateFilter(JCheckBox checkBox, ComboBox<String> modeCombo,
                                                        JBTextField field1, JBTextField field2) {
        if (!checkBox.isSelected()) {
            return null;
        }
        DateFilterMode mode = DateFilterMode.values()[modeCombo.getSelectedIndex()];
        String text1 = field1.getText().trim();
        long[] range1 = parseDateRange(text1);
        if (range1 == null) {
            return null;
        }
        String text2 = field2.getText().trim();
        long[] range2 = null;
        if (mode == DateFilterMode.IN_BETWEEN) {
            range2 = parseDateRange(text2);
            if (range2 == null) {
                return null;
            }
        }
        return new DateFilter(mode, range1[0], range1[1],
            range2 != null ? range2[0] : null,
            range2 != null ? range2[1] : null,
            text1,
            mode == DateFilterMode.IN_BETWEEN ? text2 : null);
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return nameEditor();
    }

    private static @Nullable Double parseDouble(String text) {
        try {
            return Double.valueOf(text);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    private static <T extends JComponent> T leftAligned(T component) {
        component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return component;
    }
}
