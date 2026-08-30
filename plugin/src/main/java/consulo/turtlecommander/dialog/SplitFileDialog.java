package consulo.turtlecommander.dialog;

import consulo.project.Project;
import consulo.turtlecommander.ui.TextFieldContextMenuUtil;
import consulo.turtlecommander.util.FileUtils;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBRadioButton;
import consulo.ui.ex.awt.JBTextField;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.ValidationInfo;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/** Splits one file into fixed-size chunks or into a fixed number of parts. */
public class SplitFileDialog extends DialogWrapper {
    /** Display label to chunk size in bytes; insertion order drives the combo. */
    private static final Map<String, Long> PRESET_SIZES = new LinkedHashMap<>();

    static {
        PRESET_SIZES.put("1 MB", 1L * 1024 * 1024);
        PRESET_SIZES.put("1.44 MB (Floppy)", 1_457_664L);
        PRESET_SIZES.put("5 MB", 5L * 1024 * 1024);
        PRESET_SIZES.put("10 MB", 10L * 1024 * 1024);
        PRESET_SIZES.put("100 MB (Zip)", 100L * 1024 * 1024);
        PRESET_SIZES.put("650 MB (CD)", 650L * 1024 * 1024);
        PRESET_SIZES.put("700 MB (CD)", 700L * 1024 * 1024);
        PRESET_SIZES.put("1 GB", 1L * 1024 * 1024 * 1024);
        PRESET_SIZES.put("2 GB", 2L * 1024 * 1024 * 1024);
        PRESET_SIZES.put("4 GB (FAT32)", 4L * 1024 * 1024 * 1024 - 1);
        PRESET_SIZES.put("4.7 GB (DVD)", (long) (4.7 * 1024 * 1024 * 1024));
        PRESET_SIZES.put("8.5 GB (DVD DL)", (long) (8.5 * 1024 * 1024 * 1024));
        PRESET_SIZES.put("25 GB (Blu-ray)", 25L * 1024 * 1024 * 1024);
    }

    private static final Map<String, Long> SUFFIX_MULTIPLIERS = new LinkedHashMap<>();

    static {
        SUFFIX_MULTIPLIERS.put("TB", 1L * 1024 * 1024 * 1024 * 1024);
        SUFFIX_MULTIPLIERS.put("GB", 1L * 1024 * 1024 * 1024);
        SUFFIX_MULTIPLIERS.put("MB", 1L * 1024 * 1024);
        SUFFIX_MULTIPLIERS.put("KB", 1024L);
    }

    private final String myFileName;
    private final long myFileSize;

    private final JBTextField myTargetDirField;
    private final JBRadioButton mySplitBySizeRadio = new JBRadioButton("Split by size", true);
    private final JBRadioButton mySplitByPartsRadio = new JBRadioButton("Split by number of parts");
    private final ComboBox<String> mySizeCombo;
    private final JSpinner myPartsSpinner = new JSpinner(new SpinnerNumberModel(2, 2, 9999, 1));

    public SplitFileDialog(Project project, String fileName, long fileSize, String targetDirectory) {
        super(project);
        myFileName = fileName;
        myFileSize = fileSize;
        myTargetDirField = new JBTextField(targetDirectory);

        mySizeCombo = new ComboBox<>(new DefaultComboBoxModel<>(PRESET_SIZES.keySet().toArray(new String[0])));
        mySizeCombo.setEditable(true);
        mySizeCombo.setSelectedIndex(0);
        myPartsSpinner.setEnabled(false);

        setTitle("Split File");
        setOKButtonText("Split");
        TextFieldContextMenuUtil.installStandardContextMenu(myTargetDirField);
        init();

        ButtonGroup group = new ButtonGroup();
        group.add(mySplitBySizeRadio);
        group.add(mySplitByPartsRadio);

        mySplitBySizeRadio.addActionListener(e -> {
            mySizeCombo.setEnabled(true);
            myPartsSpinner.setEnabled(false);
        });
        mySplitByPartsRadio.addActionListener(e -> {
            mySizeCombo.setEnabled(false);
            myPartsSpinner.setEnabled(true);
        });
    }

    public String getTargetDirectory() {
        return myTargetDirField.getText().trim();
    }

    public boolean isSplitBySize() {
        return mySplitBySizeRadio.isSelected();
    }

    public long getChunkSize() {
        String selectedText = String.valueOf(mySizeCombo.getEditor().getItem()).trim();
        Long preset = PRESET_SIZES.get(selectedText);
        return preset != null ? preset : parseSize(selectedText);
    }

    public int getNumberOfParts() {
        return ((Number) myPartsSpinner.getValue()).intValue();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMinimumSize(new Dimension(500, 240));
        panel.setPreferredSize(new Dimension(500, 240));

        JBLabel header = new JBLabel("Split \"" + myFileName + "\" (" + FileUtils.formatSize(myFileSize) + ")");
        header.setBorder(JBUI.Borders.emptyBottom(8));
        panel.add(leftAligned(header));

        panel.add(leftAligned(new JBLabel("Target directory:")));
        myTargetDirField.setMaximumSize(new Dimension(Integer.MAX_VALUE,
            myTargetDirField.getPreferredSize().height));
        panel.add(leftAligned(myTargetDirField));

        JPanel modes = new JPanel();
        modes.setLayout(new BoxLayout(modes, BoxLayout.Y_AXIS));
        modes.setOpaque(false);
        modes.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        modes.add(leftAligned(mySplitBySizeRadio));
        modes.add(leftAligned(indentedRow("Chunk size:", mySizeCombo)));
        modes.add(leftAligned(mySplitByPartsRadio));
        modes.add(leftAligned(indentedRow("Number of parts:", myPartsSpinner)));
        panel.add(leftAligned(modes));

        return panel;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (getTargetDirectory().isEmpty()) {
            return new ValidationInfo("Target directory cannot be empty", myTargetDirField);
        }
        if (isSplitBySize() && getChunkSize() <= 0) {
            return new ValidationInfo("Invalid chunk size", mySizeCombo);
        }
        return null;
    }

    private static JPanel indentedRow(String label, JComponent field) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = JBUI.insets(2, 20, 2, 4);
        gbc.gridx = 0;
        row.add(new JBLabel(label), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        row.add(field, gbc);
        return row;
    }

    private static long parseSize(String text) {
        String trimmed = StringUtil.toUpperCase(text.trim());
        for (Map.Entry<String, Long> entry : SUFFIX_MULTIPLIERS.entrySet()) {
            if (trimmed.endsWith(entry.getKey())) {
                String number = StringUtil.trimEnd(trimmed, entry.getKey()).trim();
                try {
                    return (long) (Double.parseDouble(number) * entry.getValue());
                }
                catch (NumberFormatException e) {
                    return 0L;
                }
            }
        }
        try {
            return Long.parseLong(trimmed);
        }
        catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static <T extends JComponent> T leftAligned(T component) {
        component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return component;
    }
}
