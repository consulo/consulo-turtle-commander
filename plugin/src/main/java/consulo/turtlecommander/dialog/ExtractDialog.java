package consulo.turtlecommander.dialog;

import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.service.OverwritePolicy;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.ui.TextFieldContextMenuUtil;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBTextField;

import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.StringJoiner;

/** Destination and overwrite policy for extracting one or more archives. */
public class ExtractDialog extends DialogWrapper {
    private static final int MAX_LISTED_ROWS = 10;

    private final List<FileEntry> mySources;
    private final JBTextField myDestinationField;
    private final ComboBox<OverwritePolicy> myPolicyCombo;

    public ExtractDialog(Project project, List<FileEntry> sources, String defaultDestination) {
        super(project);
        mySources = sources;
        myDestinationField = new JBTextField(defaultDestination);

        myPolicyCombo = new ComboBox<>(OverwritePolicy.values());
        myPolicyCombo.setRenderer(OverwritePolicyRenderer.INSTANCE);
        myPolicyCombo.setSelectedItem(TurtleCommanderSettings.getInstance().getDefaultOverwritePolicy());

        setTitle("Extract Files");
        setOKButtonText("Extract");
        TextFieldContextMenuUtil.installStandardContextMenu(myDestinationField);
        init();
    }

    public String getDestinationPath() {
        return myDestinationField.getText().trim();
    }

    public OverwritePolicy getPolicy() {
        Object selected = myPolicyCombo.getSelectedItem();
        return selected instanceof OverwritePolicy policy ? policy : OverwritePolicy.ASK;
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMinimumSize(new Dimension(500, 170));
        panel.setPreferredSize(new Dimension(500, 170));

        String label = mySources.size() == 1
            ? "Extract \"" + mySources.get(0).name() + "\" to:"
            : "Extract " + mySources.size() + " archives to:";
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

        JPanel fieldWrapper = new JPanel(new BorderLayout());
        fieldWrapper.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        fieldWrapper.add(myDestinationField, BorderLayout.CENTER);
        fieldWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE,
            myDestinationField.getPreferredSize().height + 16));
        panel.add(leftAligned(fieldWrapper));

        JPanel policyRow = new JPanel();
        policyRow.setLayout(new BoxLayout(policyRow, BoxLayout.X_AXIS));
        policyRow.add(new JBLabel("If a target file already exists:"));
        policyRow.add(Box.createHorizontalStrut(8));
        myPolicyCombo.setMaximumSize(new Dimension(260, myPolicyCombo.getPreferredSize().height));
        policyRow.add(myPolicyCombo);
        panel.add(leftAligned(policyRow));

        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myDestinationField;
    }

    private static <T extends JComponent> T leftAligned(T component) {
        component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return component;
    }
}
