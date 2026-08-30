package consulo.turtlecommander.dialog;

import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.JBLabel;
import org.jspecify.annotations.Nullable;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Dimension;
import java.util.List;
import java.util.StringJoiner;

/** Delete confirmation, with different wording for a permanent delete vs. a recycle-bin move. */
public class DeleteDialog extends DialogWrapper {
    private static final int BASE_HEIGHT = 120;
    private static final int ROW_HEIGHT = 24;
    private static final int MAX_LISTED_ROWS = 10;

    private final List<FileEntry> mySources;
    private final boolean myUseRecycleBin;
    private final @Nullable String myNote;
    private final @Nullable String myCheckboxText;

    private @Nullable JBCheckBox myCheckbox;

    /**
     * @param note         optional explanation under the prompt, e.g. why a recycle-bin delete
     *                     became permanent
     * @param checkboxText optional opt-out checkbox; read the result via {@link #isCheckboxSelected}
     */
    public DeleteDialog(Project project, List<FileEntry> sources, boolean useRecycleBin,
                        @Nullable String note, @Nullable String checkboxText) {
        super(project);
        mySources = sources;
        myUseRecycleBin = useRecycleBin;
        myNote = note;
        myCheckboxText = checkboxText;

        setTitle(useRecycleBin ? "Move to Recycle Bin" : "Delete");
        setOKButtonText(useRecycleBin ? "Move to Recycle Bin" : "Delete");
        init();
    }

    /** False when no checkbox was requested. */
    public boolean isCheckboxSelected() {
        return myCheckbox != null && myCheckbox.isSelected();
    }

    @Override
    protected JComponent createCenterPanel() {
        // The base height fits the prompt and the file list; the optional note and checkbox each
        // need an extra row.
        int extraRows = (myNote != null ? 1 : 0) + (myCheckboxText != null ? 1 : 0);
        int height = BASE_HEIGHT + extraRows * ROW_HEIGHT;

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMinimumSize(new Dimension(500, height));
        panel.setPreferredSize(new Dimension(500, height));

        panel.add(leftAligned(new JBLabel(buildPrompt(mySources, myUseRecycleBin))));

        if (myNote != null) {
            panel.add(leftAligned(new JBLabel(myNote)));
        }

        if (myCheckboxText != null) {
            myCheckbox = new JBCheckBox(myCheckboxText);
            panel.add(leftAligned(myCheckbox));
        }

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

        return panel;
    }

    /**
     * Exposed for testing: the wording depends on whether the deletion is permanent or reversible,
     * so tests assert against this without spinning up the Swing dialog.
     */
    public static String buildPrompt(List<FileEntry> sources, boolean useRecycleBin) {
        if (sources.size() == 1) {
            String name = sources.get(0).name();
            return useRecycleBin
                ? "Move \"" + name + "\" to Recycle Bin?"
                : "Delete \"" + name + "\"?";
        }
        return useRecycleBin
            ? "Move " + sources.size() + " items to Recycle Bin?"
            : "Delete " + sources.size() + " items?";
    }

    private static <T extends JComponent> T leftAligned(T component) {
        component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return component;
    }
}
