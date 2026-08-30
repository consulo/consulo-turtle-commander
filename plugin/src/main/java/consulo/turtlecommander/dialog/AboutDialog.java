package consulo.turtlecommander.dialog;

import consulo.container.plugin.PluginDescriptor;
import consulo.container.plugin.PluginManager;
import consulo.project.Project;
import consulo.turtlecommander.icon.TurtleCommanderIconGroup;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.image.ImageEffects;
import org.jspecify.annotations.Nullable;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;

/** Plugin name, version and one-line description. */
public class AboutDialog extends DialogWrapper {
    private static final float ICON_SCALE = 2.0f;

    public AboutDialog(@Nullable Project project) {
        super(project, false);
        setTitle("About Turtle Commander");
        setOKButtonText("Close");
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(JBUI.scale(12), 0));
        panel.setBorder(JBUI.Borders.empty(10));

        JBLabel iconLabel =
            new JBLabel(ImageEffects.resize(TurtleCommanderIconGroup.turtlecommander(), ICON_SCALE));
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        panel.add(iconLabel, BorderLayout.WEST);

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

        JBLabel nameLabel = new JBLabel("Turtle Commander");
        nameLabel.setFont(nameLabel.getFont().deriveFont(nameLabel.getFont().getSize2D() + 4f));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(nameLabel);

        infoPanel.add(Box.createVerticalStrut(JBUI.scale(4)));
        JBLabel versionLabel = new JBLabel("Version: " + pluginVersion());
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(versionLabel);

        infoPanel.add(Box.createVerticalStrut(JBUI.scale(8)));
        JBLabel descriptionLabel =
            new JBLabel("<html><body style='width:260px'>A dual-panel file manager</body></html>");
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(descriptionLabel);

        panel.add(infoPanel, BorderLayout.CENTER);
        return panel;
    }

    /** Resolved from this class's own loader, so the plugin id never has to be repeated here. */
    private static String pluginVersion() {
        PluginDescriptor descriptor = PluginManager.getPlugin(AboutDialog.class);
        return descriptor != null ? descriptor.getVersion() : "unknown";
    }

    /** Read-only dialog: a single Close button. */
    @Override
    protected Action[] createActions() {
        Action ok = getOKAction();
        ok.putValue(Action.NAME, "Close");
        return new Action[]{ok};
    }
}
