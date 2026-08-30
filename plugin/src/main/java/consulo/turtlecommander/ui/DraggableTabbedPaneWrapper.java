package consulo.turtlecommander.ui;

import consulo.ui.ex.awt.JBTabbedPane;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Hosts a panel's tab strip and paints the tab drag-and-drop insertion caret over it. The caret has
 * to be drawn by an ancestor: {@link javax.swing.JTabbedPane} repaints its own tab area from the
 * look and feel, so anything drawn inside it is overwritten.
 */
public class DraggableTabbedPaneWrapper extends JPanel {
    private static final Color INSERTION_CARET = new Color(0x3574F0);

    private final FileManagerPanel myPanel;

    public DraggableTabbedPaneWrapper(JBTabbedPane tabbedPane, FileManagerPanel panel) {
        super(new BorderLayout());
        myPanel = panel;
        add(tabbedPane, BorderLayout.CENTER);
        setOpaque(false);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Rectangle rect = myPanel.getDropIndicatorRect();
        if (rect == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(INSERTION_CARET);
        g2.fillRect(rect.x, rect.y, rect.width, rect.height);
    }
}
