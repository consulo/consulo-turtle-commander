package consulo.turtlecommander.dialog;

import consulo.project.Project;
import consulo.turtlecommander.ui.TextFieldContextMenuUtil;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBTextField;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/** Single-line prompt used by "create directory", "create file" and rename flows. */
public class InputDialog extends DialogWrapper {
    private final String myMessage;
    private final JBTextField myTextField;

    public InputDialog(@Nullable Project project, String title, String message, String initialValue) {
        super(project);
        myMessage = message;
        myTextField = new JBTextField(initialValue);

        setTitle(title);
        TextFieldContextMenuUtil.installStandardContextMenu(myTextField);
        init();
    }

    public String getInputValue() {
        return myTextField.getText().trim();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setPreferredSize(new Dimension(350, 60));
        panel.add(new JBLabel(myMessage), BorderLayout.NORTH);
        panel.add(myTextField, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myTextField;
    }
}
