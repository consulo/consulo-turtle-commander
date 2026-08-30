package consulo.turtlecommander.dialog;

import consulo.project.Project;
import consulo.turtlecommander.model.DriveInfo;
import consulo.turtlecommander.util.FileUtils;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBScrollPane;
import consulo.ui.ex.awt.JBUI;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/** Read-only overview of every mounted volume, one usage bar per drive. */
public class DriveSpaceDialog extends DialogWrapper {
    private static final int ROW_HEIGHT = 80;
    private static final int MAX_VISIBLE_ROWS = 6;

    private final List<DriveInfo> myDrives;

    public DriveSpaceDialog(Project project, List<DriveInfo> drives) {
        super(project);
        myDrives = drives;
        setTitle("Drive Space");
        setOKButtonText("Close");
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(8));

        for (int i = 0; i < myDrives.size(); i++) {
            panel.add(createDrivePanel(myDrives.get(i), i));
            panel.add(Box.createVerticalStrut(12));
        }

        int visibleRows = Math.min(Math.max(myDrives.size(), 1), MAX_VISIBLE_ROWS);
        JBScrollPane scrollPane = new JBScrollPane(panel);
        scrollPane.setPreferredSize(new Dimension(560, visibleRows * ROW_HEIGHT + 16));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction()};
    }

    private static JPanel createDrivePanel(DriveInfo info, int index) {
        JPanel panel = new JPanel(new BorderLayout(8, 4));
        panel.setBorder(JBUI.Borders.empty(4, 8));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

        JBLabel label = new JBLabel(info.label() + "  \u2014  " + FileUtils.formatSize(info.freeSpace())
            + " free of " + FileUtils.formatSize(info.totalSpace()));
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, BorderLayout.NORTH);

        DriveSpaceBar bar = new DriveSpaceBar(info, index);
        bar.setPreferredSize(new Dimension(0, 24));
        panel.add(bar, BorderLayout.CENTER);
        return panel;
    }

    private static class DriveSpaceBar extends JPanel {
        private static final Color[] COLORS = {
            new Color(0x4A90D9),
            new Color(0x50B86C),
            new Color(0xE8A838),
            new Color(0xD95040),
            new Color(0x9B59B6),
            new Color(0x1ABC9C),
            new Color(0xE67E22),
            new Color(0x3498DB),
        };
        private static final int ARC = 8;

        private final DriveInfo myInfo;
        private final Color myBarColor;

        DriveSpaceBar(DriveInfo info, int index) {
            myInfo = info;
            myBarColor = COLORS[index % COLORS.length];
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(new JBColor(() -> UIManager.getColor("Panel.background").darker()));
            g2.fillRoundRect(0, 0, w, h, ARC, ARC);

            double usedFraction = myInfo.totalSpace() > 0
                ? (double) myInfo.usedSpace() / myInfo.totalSpace()
                : 0.0;
            int usedWidth = Math.clamp((long) (w * usedFraction), 0, w);
            if (usedWidth > 0) {
                g2.setColor(myBarColor);
                g2.fillRoundRect(0, 0, usedWidth, h, ARC, ARC);
                // The rounded right edge only makes sense where the bar actually ends; square it
                // off when the used portion stops short of the full width.
                if (usedWidth < w - ARC) {
                    g2.fillRect(usedWidth - ARC, 0, ARC, h);
                }
            }

            long percent = myInfo.totalSpace() > 0 ? myInfo.usedSpace() * 100 / myInfo.totalSpace() : 0;
            String text = percent + "% used";
            g2.setFont(getFont().deriveFont(11f));
            g2.setColor(new JBColor(() -> UIManager.getColor("Label.foreground")));
            FontMetrics metrics = g2.getFontMetrics();
            g2.drawString(text, (w - metrics.stringWidth(text)) / 2, (h + metrics.getAscent() - metrics.getDescent()) / 2);

            g2.dispose();
        }
    }
}
