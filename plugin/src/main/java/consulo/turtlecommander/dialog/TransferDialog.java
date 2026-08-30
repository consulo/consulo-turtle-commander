package consulo.turtlecommander.dialog;

import consulo.application.progress.ProgressManager;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.service.OverwritePolicy;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.ui.TextFieldContextMenuUtil;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBTextField;
import consulo.ui.ex.awt.Messages;
import org.jspecify.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;

/**
 * Shared destination / overwrite-policy dialog used for both Copy and Move. The two flows are
 * structurally identical — only the window title, OK-button label, and a few wording bits change —
 * so they are parameterized via a verb ("Copy" / "Move") instead of being two near-identical
 * {@link DialogWrapper} subclasses.
 */
public class TransferDialog extends DialogWrapper {
    private static final int MAX_LISTED_ROWS = 10;

    private final Project myProject;
    private final String myVerb;
    private final List<FileEntry> mySources;
    private final Path myDestination;
    private final String myDestinationDisplayPath;

    private final JBTextField myDestinationField;
    private final ComboBox<OverwritePolicy> myPolicyCombo;

    private Path myTargetDirectory;

    public TransferDialog(Project project, String verb, List<FileEntry> sources, Path destination) {
        this(project, verb, sources, destination, destination.toString());
    }

    public TransferDialog(Project project, String verb, List<FileEntry> sources, Path destination,
                          String destinationDisplayPath) {
        super(project);
        myProject = project;
        myVerb = verb;
        mySources = sources;
        myDestination = destination;
        myDestinationDisplayPath = destinationDisplayPath;
        myTargetDirectory = destination;

        myDestinationField = new JBTextField(destinationDisplayPath);

        myPolicyCombo = new ComboBox<>(OverwritePolicy.values());
        myPolicyCombo.setRenderer(OverwritePolicyRenderer.INSTANCE);
        // Preselect the user's saved default policy. ASK on a fresh install means the dialog still
        // prompts per file — overwrite-without-asking has to be opted into.
        myPolicyCombo.setSelectedItem(TurtleCommanderSettings.getInstance().getDefaultOverwritePolicy());

        setTitle(verb);
        setOKButtonText(verb);
        TextFieldContextMenuUtil.installStandardContextMenu(myDestinationField);
        init();
    }

    public OverwritePolicy getPolicy() {
        Object selected = myPolicyCombo.getSelectedItem();
        return selected instanceof OverwritePolicy policy ? policy : OverwritePolicy.ASK;
    }

    public Path getTargetDirectory() {
        return myTargetDirectory;
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMinimumSize(new Dimension(500, 140));
        panel.setPreferredSize(new Dimension(500, 140));

        String label = mySources.size() == 1
            ? myVerb + " \"" + mySources.get(0).name() + "\" to:"
            : myVerb + " " + mySources.size() + " items to:";
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

        panel.add(Box.createVerticalStrut(8));
        myDestinationField.setMaximumSize(new Dimension(Integer.MAX_VALUE,
            myDestinationField.getPreferredSize().height));
        panel.add(leftAligned(myDestinationField));
        panel.add(Box.createVerticalStrut(8));

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

    @Override
    protected void doOKAction() {
        String text = myDestinationField.getText().trim();
        if (text.isEmpty()) {
            Messages.showErrorDialog(getContentPanel(), "Destination cannot be empty.", myVerb);
            return;
        }

        Path resolved;
        if (text.equals(myDestinationDisplayPath)) {
            resolved = myDestination;
        }
        else {
            Path parsed;
            try {
                parsed = Path.of(text);
            }
            catch (InvalidPathException e) {
                Messages.showErrorDialog(getContentPanel(), "Invalid destination path: " + e.getMessage(), myVerb);
                return;
            }
            resolved = parsed.isAbsolute() ? parsed.normalize() : myDestination.resolve(parsed).normalize();
        }

        // The stat / mkdir can block for the SMB timeout on a dead network destination — run it
        // under a modal progress so the EDT keeps painting, and keep the dialog open on failure so
        // the user can correct the path.
        Path checked = resolved;
        String error = ProgressManager.getInstance().<String, RuntimeException>runProcessWithProgressSynchronously(
            () -> prepareDestination(checked),
            LocalizeValue.localizeTODO("Checking destination\u2026"),
            false,
            myProject);
        if (error != null) {
            Messages.showErrorDialog(getContentPanel(), error, myVerb);
            return;
        }

        myTargetDirectory = resolved;
        super.doOKAction();
    }

    /** Returns an error message, or {@code null} when the destination is usable. */
    private static @Nullable String prepareDestination(Path destination) {
        if (Files.exists(destination)) {
            return Files.isDirectory(destination) ? null : "Destination is not a directory:\n" + destination;
        }
        try {
            Files.createDirectories(destination);
            return null;
        }
        catch (IOException e) {
            return "Failed to create destination directory:\n" + e.getMessage();
        }
    }

    private static <T extends JComponent> T leftAligned(T component) {
        component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return component;
    }
}
