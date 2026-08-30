package consulo.turtlecommander.dialog;

import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.operation.ArchiveFormat;
import consulo.turtlecommander.ui.TextFieldContextMenuUtil;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBTextField;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.StringJoiner;

/** Destination archive, format and post-pack cleanup for a Pack operation. */
public class PackDialog extends DialogWrapper {
    private static final int MAX_LISTED_ROWS = 10;

    private final List<FileEntry> mySources;
    private final JBTextField myArchiveField;
    private final ComboBox<ArchiveFormat> myFormatCombo;
    private final JCheckBox myDeleteCheckBox = new JCheckBox("Delete files after packing", false);

    public PackDialog(Project project, List<FileEntry> sources, String defaultArchivePath) {
        super(project);
        mySources = sources;
        myArchiveField = new JBTextField(defaultArchivePath);

        myFormatCombo = new ComboBox<>(new DefaultComboBoxModel<>(ArchiveFormat.values()));
        myFormatCombo.setSelectedItem(ArchiveFormat.ZIP);
        DefaultListCellRenderer delegate = new DefaultListCellRenderer();
        ListCellRenderer<ArchiveFormat> renderer = (list, value, index, isSelected, cellHasFocus) ->
            delegate.getListCellRendererComponent(list, value == null ? null : value.label(), index,
                isSelected, cellHasFocus);
        myFormatCombo.setRenderer(renderer);

        setTitle("Pack Files");
        setOKButtonText("Pack");
        TextFieldContextMenuUtil.installStandardContextMenu(myArchiveField);
        init();

        myFormatCombo.addActionListener(e -> {
            String path = myArchiveField.getText().trim();
            String swapped = withExtensionOf(path, getArchiveFormat());
            if (swapped != null) {
                myArchiveField.setText(swapped);
            }
        });
    }

    public String getArchivePath() {
        String path = myArchiveField.getText().trim();
        String swapped = withExtensionOf(path, getArchiveFormat());
        return swapped != null ? swapped : path;
    }

    public ArchiveFormat getArchiveFormat() {
        Object selected = myFormatCombo.getSelectedItem();
        return selected instanceof ArchiveFormat format ? format : ArchiveFormat.ZIP;
    }

    public boolean isDeleteAfterPacking() {
        return myDeleteCheckBox.isSelected();
    }

    /**
     * Swaps a recognized archive extension for the selected format's. Returns {@code null} when the
     * path carries none, so a name the user typed by hand is left alone.
     */
    private static @Nullable String withExtensionOf(String path, ArchiveFormat format) {
        for (ArchiveFormat candidate : ArchiveFormat.values()) {
            if (path.endsWith(candidate.extension())) {
                return StringUtil.trimEnd(path, candidate.extension()) + format.extension();
            }
        }
        return null;
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMinimumSize(new Dimension(500, 150));
        panel.setPreferredSize(new Dimension(500, 150));

        String label = mySources.size() == 1
            ? "Pack \"" + mySources.get(0).name() + "\" to archive:"
            : "Pack " + mySources.size() + " items to archive:";
        panel.add(leftAligned(new JBLabel(label)));

        if (mySources.size() > 1) {
            StringJoiner names = new StringJoiner("\n");
            for (FileEntry entry : mySources) {
                names.add(entry.name());
            }
            JTextArea textArea = new JTextArea(names.toString());
            textArea.setEditable(false);
            textArea.setRows(Math.min(mySources.size(), MAX_LISTED_ROWS));
            panel.add(leftAligned(new JScrollPane(textArea)));
        }

        JPanel fieldPanel = new JPanel(new BorderLayout());
        fieldPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        fieldPanel.add(myArchiveField, BorderLayout.CENTER);
        fieldPanel.add(myFormatCombo, BorderLayout.EAST);
        fieldPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE,
            myArchiveField.getPreferredSize().height + 16));
        panel.add(leftAligned(fieldPanel));

        panel.add(leftAligned(myDeleteCheckBox));
        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myArchiveField;
    }

    private static <T extends JComponent> T leftAligned(T component) {
        component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return component;
    }
}
