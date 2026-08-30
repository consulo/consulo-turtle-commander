package consulo.turtlecommander.ui;

import consulo.turtlecommander.settings.ComponentStyle;
import consulo.ui.ex.JBColor;
import consulo.util.lang.StringUtil;
import consulo.ui.ex.awt.JBTextField;
import consulo.ui.ex.awt.JBUI;
import org.jspecify.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The path bar: a clickable breadcrumb trail that swaps to a plain editable field when clicked.
 */
public class BreadcrumbPathField extends JPanel {
    private static final String BREADCRUMBS = "breadcrumbs";
    private static final String EDITOR = "editor";
    private static final String SEPARATOR = " \u203A ";
    private static final String ELLIPSIS = "\u2026";

    private static final int MIN_WIDTH = 40;
    private static final int MAX_PREFERRED_WIDTH = 200;
    private static final int DESIRED_NAME_CAP = 30;

    /** One crumb: what to show, and where clicking it navigates. */
    record PathSegment(String name, String fullPath) {
    }

    private final CardLayout myCardLayout = new CardLayout();
    private final JPanel myBreadcrumbContent = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JPanel myBreadcrumbPanel = new JPanel(new GridBagLayout());
    private final JBTextField myEditField = new JBTextField();

    private final Font myDefaultEditFieldFont;
    private final @Nullable Color myDefaultEditFieldFg = UIManager.getColor("TextField.foreground");
    private final @Nullable Color myDefaultEditFieldBg = UIManager.getColor("TextField.background");

    private String myText = "";
    private boolean myEditMode;

    /**
     * Width the trail was last laid out for. Lets the resize listener skip rebuilds that wouldn't
     * change the fitting, and avoids a revalidate feedback loop.
     */
    private int myLastLayoutWidth = -1;

    private @Nullable Font myCustomFont;
    private @Nullable Color myCustomFg;
    private @Nullable Consumer<String> myOnSegmentClick;

    public BreadcrumbPathField() {
        myDefaultEditFieldFont = myEditField.getFont();

        setLayout(myCardLayout);
        setOpaque(false);
        setBorder(null);

        myBreadcrumbContent.setOpaque(false);
        myBreadcrumbContent.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        myBreadcrumbContent.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                switchToEditMode();
            }
        });

        myBreadcrumbPanel.setOpaque(true);
        myBreadcrumbPanel.setBackground(myEditField.getBackground());
        myBreadcrumbPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border()), JBUI.Borders.empty(2, 5)));
        myBreadcrumbPanel.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 1.0;
        myBreadcrumbPanel.add(myBreadcrumbContent, gbc);

        myBreadcrumbPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                switchToEditMode();
            }
        });

        // Re-fit whenever the available width changes so the tail stays visible as the splitter
        // moves. Guarded on the computed width to avoid looping with rebuildBreadcrumbs' revalidate.
        myBreadcrumbPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (availableBreadcrumbWidth() != myLastLayoutWidth) {
                    rebuildBreadcrumbs();
                }
            }
        });

        add(myBreadcrumbPanel, BREADCRUMBS);
        add(myEditField, EDITOR);
        myCardLayout.show(this, BREADCRUMBS);

        myEditField.addActionListener(e -> SwingUtilities.invokeLater(this::switchToBreadcrumbMode));

        myEditField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (e.isTemporary()) {
                    return;
                }
                // Focus moving into the field's own context menu isn't leaving the field.
                java.awt.Component opposite = e.getOppositeComponent();
                JComponent popup = myEditField.getComponentPopupMenu();
                if (opposite != null && popup != null && SwingUtilities.isDescendingFrom(opposite, popup)) {
                    return;
                }
                switchToBreadcrumbMode();
            }
        });

        TextFieldContextMenuUtil.installStandardContextMenu(myEditField);

        myEditField.registerKeyboardAction(e -> switchToBreadcrumbMode(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_FOCUSED);
    }

    public String getText() {
        return myEditMode ? myEditField.getText() : myText;
    }

    public void setText(String text) {
        myText = text;
        myEditField.setText(text);
        rebuildBreadcrumbs();
        if (myEditMode) {
            switchToBreadcrumbMode();
        }
    }

    public void setOnSegmentClick(@Nullable Consumer<String> onSegmentClick) {
        myOnSegmentClick = onSegmentClick;
    }

    public void addActionListener(ActionListener listener) {
        myEditField.addActionListener(listener);
    }

    /**
     * A {@link FlowLayout}'s minimum width is the full single-row width of every segment. Left
     * unchecked that propagates through the header into the splitter — which honours minimum sizes
     * — so a deep path (an archive inside an archive) would force this panel wider. Reporting a
     * small fixed minimum keeps the field filling its slot while the crumbs clip instead.
     */
    @Override
    public Dimension getMinimumSize() {
        return new Dimension(JBUI.scale(MIN_WIDTH), super.getMinimumSize().height);
    }

    /** Same reasoning for the preferred width; height still follows the content. */
    @Override
    public Dimension getPreferredSize() {
        Dimension pref = super.getPreferredSize();
        return new Dimension(Math.min(pref.width, JBUI.scale(MAX_PREFERRED_WIDTH)), pref.height);
    }

    public void applyStyle(ComponentStyle style) {
        myCustomFont = style.getFont(myDefaultEditFieldFont);
        myCustomFg = style.parsedFontColor();

        myEditField.setFont(myCustomFont != null ? myCustomFont : myDefaultEditFieldFont);
        myEditField.setForeground(myCustomFg != null ? myCustomFg : myDefaultEditFieldFg);

        Color bg = style.parsedBackgroundColor();
        if (bg != null) {
            myEditField.setBackground(bg);
            setOpaque(true);
            setBackground(bg);
            myBreadcrumbContent.setOpaque(true);
            myBreadcrumbContent.setBackground(bg);
            myBreadcrumbPanel.setOpaque(true);
            myBreadcrumbPanel.setBackground(bg);
        }
        else {
            myEditField.setBackground(myDefaultEditFieldBg);
            setOpaque(false);
            myBreadcrumbContent.setOpaque(false);
            myBreadcrumbPanel.setOpaque(false);
        }

        rebuildBreadcrumbs();
    }

    private void switchToEditMode() {
        myEditMode = true;
        myEditField.setText(myText);
        myCardLayout.show(this, EDITOR);
        myEditField.requestFocusInWindow();
        myEditField.selectAll();
    }

    private void switchToBreadcrumbMode() {
        myEditMode = false;
        myCardLayout.show(this, BREADCRUMBS);
    }

    private void rebuildBreadcrumbs() {
        myBreadcrumbContent.removeAll();

        List<PathSegment> segments = splitPath(myText);
        JBColor linkColor = new JBColor(new Color(0x2470B3), new Color(0x589DF6));

        int available = availableBreadcrumbWidth();
        myLastLayoutWidth = available;

        Font metricsFont = myCustomFont != null ? myCustomFont : UIManager.getFont("Label.font");
        FontMetrics fm = myBreadcrumbContent.getFontMetrics(metricsFont);
        List<String> displays = fitSegments(segments, available, fm);

        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                myBreadcrumbContent.add(createSeparator());
            }
            myBreadcrumbContent.add(createSegmentLabel(segments.get(i), displays.get(i), linkColor));
        }

        myBreadcrumbContent.revalidate();
        myBreadcrumbContent.repaint();
    }

    private JLabel createSeparator() {
        JLabel separator = new JLabel(SEPARATOR);
        if (myCustomFont != null) {
            separator.setFont(myCustomFont);
        }
        separator.setForeground(JBColor.GRAY);
        separator.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                e.consume();
                switchToEditMode();
            }
        });
        return separator;
    }

    private JLabel createSegmentLabel(PathSegment segment, String display, Color linkColor) {
        JLabel label = new JLabel(display);
        if (myCustomFont != null) {
            label.setFont(myCustomFont);
        }
        if (myCustomFg != null) {
            label.setForeground(myCustomFg);
        }
        label.setToolTipText(segment.name());
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Color defaultFg = label.getForeground();
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                e.consume();
                Consumer<String> onSegmentClick = myOnSegmentClick;
                if (onSegmentClick != null) {
                    onSegmentClick.accept(segment.fullPath());
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                label.setForeground(linkColor);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                label.setForeground(defaultFg);
            }
        });
        return label;
    }

    /** Width inside the panel's border the trail may occupy, or -1 when not yet laid out. */
    private int availableBreadcrumbWidth() {
        int width = myBreadcrumbPanel.getWidth();
        if (width <= 0) {
            return -1;
        }
        Insets insets = myBreadcrumbPanel.getInsets();
        return width - insets.left - insets.right;
    }

    /**
     * Chooses each segment's display text so the whole trail fits {@code available} pixels,
     * favouring the tail: the deepest segments keep as much of their name as possible while leading
     * ones are squeezed first. Every segment is still rendered — down to a single letter plus
     * ellipsis — and each label keeps the full name as a tooltip, so any ancestor stays clickable.
     * Falls back to the character cap when the width isn't known yet; the resize listener re-fits.
     */
    private static List<String> fitSegments(List<PathSegment> segments, int available, FontMetrics fm) {
        List<String> desired = new ArrayList<>(segments.size());
        for (PathSegment segment : segments) {
            desired.add(StringUtil.trimMiddle(segment.name(), DESIRED_NAME_CAP));
        }
        if (available <= 0 || segments.isEmpty()) {
            return desired;
        }

        int budget = available - fm.stringWidth(SEPARATOR) * (segments.size() - 1);

        List<String> minDisplays = new ArrayList<>(segments.size());
        for (PathSegment segment : segments) {
            minDisplays.add(minimalDisplay(segment.name()));
        }
        if (budget <= 0) {
            return minDisplays;
        }

        int[] desiredW = new int[segments.size()];
        int desiredTotal = 0;
        for (int i = 0; i < segments.size(); i++) {
            desiredW[i] = fm.stringWidth(desired.get(i));
            desiredTotal += desiredW[i];
        }
        if (desiredTotal <= budget) {
            return desired;
        }

        int[] minW = new int[segments.size()];
        for (int i = 0; i < segments.size(); i++) {
            minW[i] = fm.stringWidth(minDisplays.get(i));
        }

        String[] result = new String[segments.size()];
        int used = 0;
        // Allocate from the tail toward the head, reserving each earlier segment's minimal width so
        // the head can never be starved below one letter.
        for (int i = segments.size() - 1; i >= 0; i--) {
            int reservedForEarlier = 0;
            for (int j = 0; j < i; j++) {
                reservedForEarlier += minW[j];
            }

            int availForThis = budget - used - reservedForEarlier;
            String text;
            if (availForThis <= minW[i]) {
                text = minDisplays.get(i);
            }
            else if (desiredW[i] <= availForThis) {
                text = desired.get(i);
            }
            else {
                text = truncateMiddleToWidth(desired.get(i), availForThis, fm);
            }

            result[i] = text;
            used += fm.stringWidth(text);
        }
        return List.of(result);
    }

    private static String minimalDisplay(String name) {
        return name.length() <= 1 ? name : name.charAt(0) + ELLIPSIS;
    }

    /** Longest middle-elided form of {@code s} whose rendered width fits {@code maxWidth}. */
    private static String truncateMiddleToWidth(String s, int maxWidth, FontMetrics fm) {
        if (fm.stringWidth(s) <= maxWidth) {
            return s;
        }

        int left = (s.length() + 1) / 2;
        int right = s.length() / 2;
        while (left + right > 1) {
            String candidate = s.substring(0, left) + ELLIPSIS + s.substring(s.length() - right);
            if (fm.stringWidth(candidate) <= maxWidth) {
                return candidate;
            }
            if (left >= right && left > 0) {
                left--;
            }
            else if (right > 0) {
                right--;
            }
        }
        return minimalDisplay(s);
    }


    static List<PathSegment> splitPath(String pathStr) {
        if (pathStr.isEmpty()) {
            return List.of();
        }

        List<PathSegment> segments = new ArrayList<>();
        boolean unc = pathStr.length() >= 2 && pathStr.charAt(0) == '\\' && pathStr.charAt(1) == '\\';
        boolean windows = pathStr.length() >= 2 && pathStr.charAt(1) == ':';

        if (unc) {
            // \\server\share\a\b -> one root crumb "\\server\share" (there is nothing above the
            // share), then a, b. A bare "\\server" with no share stays the root crumb.
            List<String> parts = nonEmptyParts(pathStr.substring(2), "\\\\");
            if (parts.isEmpty()) {
                return List.of();
            }
            int rootEnd = parts.size() >= 2 ? 2 : 1;
            String rootPath = "\\\\" + String.join("\\", parts.subList(0, rootEnd));
            segments.add(new PathSegment(rootPath, rootPath));
            for (int i = rootEnd; i < parts.size(); i++) {
                segments.add(new PathSegment(parts.get(i), "\\\\" + String.join("\\", parts.subList(0, i + 1))));
            }
            return segments;
        }

        if (windows) {
            String[] parts = pathStr.split("\\\\", -1);
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].isEmpty()) {
                    continue;
                }
                String displayName = i == 0 ? parts[i] + "\\" : parts[i];
                String fullPath = i == 0 ? parts[i] + "\\" : String.join("\\", List.of(parts).subList(0, i + 1));
                segments.add(new PathSegment(displayName, fullPath));
            }
            return segments;
        }

        String[] parts = pathStr.split("/", -1);
        if (parts.length > 0 && parts[0].isEmpty()) {
            segments.add(new PathSegment("/", "/"));
        }
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            String joined = String.join("/", List.of(parts).subList(0, i + 1));
            segments.add(new PathSegment(parts[i], joined.isEmpty() ? "/" : joined));
        }
        return segments;
    }

    private static List<String> nonEmptyParts(String value, String regex) {
        List<String> parts = new ArrayList<>();
        for (String part : value.split(regex, -1)) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }
}
