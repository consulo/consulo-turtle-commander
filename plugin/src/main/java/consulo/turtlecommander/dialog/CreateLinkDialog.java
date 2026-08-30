package consulo.turtlecommander.dialog;

import consulo.application.progress.ProgressManager;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.TextFieldContextMenuUtil;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBTextField;
import consulo.ui.ex.awt.Messages;
import org.jspecify.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Dual-pane "create link" dialog: the active panel's sources become links in the opposite panel's
 * destination directory. For a single source the link path (location + name) is editable so the
 * link can be renamed; for several, the field is the location directory and each link keeps its
 * source's name. The <b>Link type</b> choice selects a symbolic or a hard link. For symbolic links
 * the <b>Points to</b> choice controls whether each link stores an absolute target or one relative
 * to the link's own directory (relative makes the link portable when source and destination live on
 * the same root); hard links always reference the existing file directly, so that choice does not
 * apply to them.
 */
public class CreateLinkDialog extends DialogWrapper {
    private static final int MAX_LISTED_ROWS = 8;

    public record LinkSpec(Path link, Path target, boolean hard) {
    }

    private final Project myProject;
    private final List<FileEntry> mySources;
    private final Path myDestination;
    private final boolean mySingle;

    private final JBTextField myLinkField;
    private final JRadioButton mySymbolicRadio = new JRadioButton("Symbolic link");
    private final JRadioButton myHardRadio = new JRadioButton("Hard link");
    private final JRadioButton myAbsoluteRadio = new JRadioButton("Absolute path");
    private final JRadioButton myRelativeRadio = new JRadioButton("Relative to link");

    private List<LinkSpec> mySpecs = List.of();

    public CreateLinkDialog(Project project, List<FileEntry> sources, Path destination) {
        this(project, sources, destination, destination.toString());
    }

    public CreateLinkDialog(Project project, List<FileEntry> sources, Path destination,
                            String destinationDisplayPath) {
        super(project);
        myProject = project;
        mySources = sources;
        myDestination = destination;
        mySingle = sources.size() == 1;
        myLinkField = new JBTextField(mySingle
            ? destination.resolve(sources.get(0).name()).toString()
            : destinationDisplayPath);

        setTitle("Create Link");
        setOKButtonText("Create");

        mySymbolicRadio.setSelected(true);
        ButtonGroup typeGroup = new ButtonGroup();
        typeGroup.add(mySymbolicRadio);
        typeGroup.add(myHardRadio);

        // Default to relative only when every source shares the destination's root; relativize
        // across roots (different drives) is impossible, so fall back to absolute there.
        boolean sameRoot = sources.stream()
            .allMatch(entry -> Objects.equals(entry.path().getRoot(), destination.getRoot()));
        myRelativeRadio.setSelected(sameRoot);
        myAbsoluteRadio.setSelected(!sameRoot);
        ButtonGroup pointsGroup = new ButtonGroup();
        pointsGroup.add(myAbsoluteRadio);
        pointsGroup.add(myRelativeRadio);

        mySymbolicRadio.addItemListener(e -> syncPointsTo());
        syncPointsTo();

        TextFieldContextMenuUtil.installStandardContextMenu(myLinkField);
        init();
    }

    /** Computed in {@link #doOKAction()}; the links to create. */
    public List<LinkSpec> getSpecs() {
        return mySpecs;
    }

    /**
     * The absolute/relative distinction is meaningless for hard links — they always point at the
     * existing file's data — so grey it out unless a symbolic link is selected.
     */
    private void syncPointsTo() {
        myAbsoluteRadio.setEnabled(mySymbolicRadio.isSelected());
        myRelativeRadio.setEnabled(mySymbolicRadio.isSelected());
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(560, mySingle ? 185 : 255));

        String sourceLabel = mySingle
            ? "Link to \"" + mySources.get(0).name() + "\":"
            : "Link to " + mySources.size() + " items in:";
        panel.add(leftAligned(new JBLabel(sourceLabel)));

        if (!mySingle) {
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
        panel.add(leftAligned(new JBLabel(mySingle ? "Link path:" : "Location:")));
        myLinkField.setMaximumSize(new Dimension(Integer.MAX_VALUE, myLinkField.getPreferredSize().height));
        panel.add(leftAligned(myLinkField));

        panel.add(Box.createVerticalStrut(8));
        panel.add(leftAligned(radioRow("Link type:", mySymbolicRadio, myHardRadio)));

        panel.add(Box.createVerticalStrut(8));
        panel.add(leftAligned(radioRow("Points to:", myAbsoluteRadio, myRelativeRadio)));

        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myLinkField;
    }

    @Override
    protected void doOKAction() {
        String text = myLinkField.getText().trim();
        if (text.isEmpty()) {
            showError("Link path cannot be empty.");
            return;
        }

        Path entered;
        try {
            entered = Path.of(text);
        }
        catch (InvalidPathException e) {
            showError("Invalid path: " + e.getMessage());
            return;
        }
        Path resolved = entered.isAbsolute() ? entered.normalize() : myDestination.resolve(entered).normalize();

        boolean hard = myHardRadio.isSelected();
        // Hard links cannot reference directories — reject up front rather than failing per item.
        if (hard) {
            StringJoiner directories = new StringJoiner("\n");
            for (FileEntry entry : mySources) {
                if (entry.directory()) {
                    directories.add(entry.name());
                }
            }
            if (directories.length() > 0) {
                showError("Hard links cannot point to directories:\n" + directories);
                return;
            }
        }

        List<LinkSpec> computed = new ArrayList<>();
        if (mySingle) {
            if (resolved.getFileName() == null) {
                showError("Link path must include a name.");
                return;
            }
            Path linkDir = resolved.getParent() != null ? resolved.getParent() : myDestination;
            computed.add(new LinkSpec(resolved, targetFor(mySources.get(0).path(), linkDir, hard), hard));
        }
        else {
            for (FileEntry entry : mySources) {
                computed.add(new LinkSpec(resolved.resolve(entry.name()),
                    targetFor(entry.path(), resolved, hard), hard));
            }
        }

        // Refuse to clobber anything already at a link path (NOFOLLOW so a link itself counts).
        // The stats run under a modal progress: a dead network destination would otherwise freeze
        // the EDT for the SMB timeout per link.
        LinkSpec clash = ProgressManager.getInstance().<LinkSpec, RuntimeException>runProcessWithProgressSynchronously(
            () -> firstExisting(computed),
            LocalizeValue.localizeTODO("Checking link paths\u2026"),
            false,
            myProject);
        if (clash != null) {
            showError("Already exists:\n" + clash.link());
            return;
        }

        mySpecs = computed;
        super.doOKAction();
    }

    private static @Nullable LinkSpec firstExisting(List<LinkSpec> specs) {
        for (LinkSpec spec : specs) {
            if (Files.exists(spec.link(), LinkOption.NOFOLLOW_LINKS)) {
                return spec;
            }
        }
        return null;
    }

    /**
     * The link's target. Hard links always reference the existing file by absolute path; for
     * symbolic links it is absolute or relative to the link's own directory per the user's choice.
     */
    private Path targetFor(Path source, Path linkDir, boolean hard) {
        Path absolute = source.toAbsolutePath().normalize();
        if (hard || myAbsoluteRadio.isSelected()) {
            return absolute;
        }
        try {
            return linkDir.toAbsolutePath().normalize().relativize(absolute);
        }
        catch (IllegalArgumentException e) {
            return absolute;
        }
    }

    private void showError(String message) {
        Messages.showErrorDialog(getContentPanel(), message, "Create Link");
    }

    private static JPanel radioRow(String label, JRadioButton first, JRadioButton second) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(new JBLabel(label));
        row.add(Box.createHorizontalStrut(8));
        row.add(first);
        row.add(Box.createHorizontalStrut(8));
        row.add(second);
        return row;
    }

    private static <T extends JComponent> T leftAligned(T component) {
        component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return component;
    }
}
