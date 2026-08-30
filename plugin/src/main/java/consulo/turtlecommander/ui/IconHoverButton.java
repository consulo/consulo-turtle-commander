package consulo.turtlecommander.ui;

import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Icon;

/**
 * A borderless 16x16 icon button that swaps its icon on hover — the shape shared by the tab-strip's
 * "new tab" and "close tab" affordances.
 */
public class IconHoverButton extends JComponent {
    private static final int SIZE = 16;

    private final Icon myIcon;
    private final Icon myHoverIcon;
    private final Runnable myOnClick;

    private boolean myHovered;

    public IconHoverButton(Image icon, Image hoverIcon, @Nullable String tooltip, Runnable onClick) {
        myIcon = TargetAWT.to(icon);
        myHoverIcon = TargetAWT.to(hoverIcon);
        myOnClick = onClick;

        setPreferredSize(new Dimension(SIZE, SIZE));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setOpaque(false);
        if (tooltip != null) {
            setToolTipText(tooltip);
        }

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                myHovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                myHovered = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    myOnClick.run();
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Icon icon = myHovered ? myHoverIcon : myIcon;
        icon.paintIcon(this, g, (getWidth() - icon.getIconWidth()) / 2, (getHeight() - icon.getIconHeight()) / 2);
    }
}
