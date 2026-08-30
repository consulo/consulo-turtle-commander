package consulo.turtlecommander.ui;

import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;

import javax.swing.BorderFactory;
import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Flat toggle for one of the four view modes, drawing its own subtle selected background. */
public class ViewModeButton extends JToggleButton {
    private static final int SIZE = 24;
    private static final int CORNER = 6;

    private static final JBColor SELECTED_BACKGROUND =
        new JBColor(new Color(0, 0, 0, 30), new Color(255, 255, 255, 40));

    public ViewModeButton(Image icon, String tooltip, boolean selected) {
        super(TargetAWT.to(icon), selected);

        setToolTipText(tooltip);
        setFocusable(false);
        setPreferredSize(new Dimension(SIZE, SIZE));
        setMargin(JBUI.emptyInsets());
        setContentAreaFilled(false);
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (isSelected()) {
            Graphics2D g2 = (Graphics2D)g;
            g2.setColor(SELECTED_BACKGROUND);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER, CORNER);
        }
        super.paintComponent(g);
    }
}
