package consulo.turtlecommander.dialog;

import consulo.project.Project;
import consulo.turtlecommander.ui.TextFieldContextMenuUtil;
import consulo.turtlecommander.util.FileUtils;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBTextField;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.ValidationInfo;
import org.jspecify.annotations.Nullable;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Dimension;

/** Target directory and file name for reassembling split parts. */
public class CombineFilesDialog extends DialogWrapper {
    private final int myChunkCount;
    private final long myTotalSize;
    private final boolean myHasCrc;

    private final JBTextField myTargetDirField;
    private final JBTextField myTargetFileField;

    public CombineFilesDialog(Project project, int chunkCount, long totalSize, boolean hasCrc,
                              String targetDirectory, String targetFileName) {
        super(project);
        myChunkCount = chunkCount;
        myTotalSize = totalSize;
        myHasCrc = hasCrc;
        myTargetDirField = new JBTextField(targetDirectory);
        myTargetFileField = new JBTextField(targetFileName);

        setTitle("Combine Files");
        setOKButtonText("Combine");
        TextFieldContextMenuUtil.installStandardContextMenu(myTargetDirField);
        TextFieldContextMenuUtil.installStandardContextMenu(myTargetFileField);
        init();
    }

    public String getTargetDirectory() {
        return myTargetDirField.getText().trim();
    }

    public String getTargetFile() {
        return myTargetFileField.getText().trim();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMinimumSize(new Dimension(500, 160));
        panel.setPreferredSize(new Dimension(500, 160));

        String info = myChunkCount + " parts, " + FileUtils.formatSize(myTotalSize) + " total"
            + (myHasCrc ? " (CRC will be verified)" : "");
        JBLabel infoLabel = leftAligned(new JBLabel(info));
        infoLabel.setBorder(JBUI.Borders.emptyBottom(8));
        panel.add(infoLabel);

        panel.add(leftAligned(new JBLabel("Target directory:")));
        panel.add(fullWidth(myTargetDirField));

        JBLabel fileLabel = leftAligned(new JBLabel("Target file name:"));
        fileLabel.setBorder(JBUI.Borders.emptyTop(8));
        panel.add(fileLabel);
        panel.add(fullWidth(myTargetFileField));

        return panel;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (getTargetDirectory().isEmpty()) {
            return new ValidationInfo("Target directory cannot be empty", myTargetDirField);
        }
        if (getTargetFile().isEmpty()) {
            return new ValidationInfo("Target file name cannot be empty", myTargetFileField);
        }
        return null;
    }

    private static <T extends JComponent> T leftAligned(T component) {
        component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return component;
    }

    private static JBTextField fullWidth(JBTextField field) {
        field.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
        return field;
    }
}
