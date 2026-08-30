package consulo.turtlecommander.settings;

import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.JBUI;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Column visibility, order and per-column styling: a checkbox list on the left with Up/Down, and
 * the selected column's style controls on the right.
 */
public class ColumnsEditor {
    /** Clicks left of this offset land on the row's checkbox rather than selecting it. */
    private static final int CHECKBOX_HIT_WIDTH = 24;
    private static final int PANEL_HEIGHT = 260;

    /** One row: the column's identity and visibility, plus its own style editor. */
    private static final class ColumnRow {
        private final String myId;
        private final ComponentStyleEditor myStyleEditor;
        private boolean myVisible;

        ColumnRow(String id, boolean visible, ComponentStyleEditor styleEditor) {
            myId = id;
            myVisible = visible;
            myStyleEditor = styleEditor;
        }
    }

    private final List<ColumnRow> myRows = new ArrayList<>();
    private final DefaultListModel<ColumnRow> myListModel = new DefaultListModel<>();
    private final JBList<ColumnRow> myList = new JBList<>(myListModel);
    private final JPanel myDetailPanel = new JPanel(new BorderLayout());
    private final JPanel myPanel;

    public ColumnsEditor(List<ColumnConfig> initialColumns) {
        resetFrom(initialColumns);

        myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        myList.setCellRenderer(new CheckBoxRenderer());
        myList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleVisibilityIfOnCheckBox(e);
            }
        });
        myList.addListSelectionListener(e -> updateDetail());

        myPanel = buildPanel();
        if (!myRows.isEmpty()) {
            myList.setSelectedIndex(0);
        }
    }

    public JPanel getPanel() {
        return myPanel;
    }

    private JPanel buildPanel() {
        JButton moveUpButton = new JButton("Up");
        moveUpButton.addActionListener(e -> move(-1));
        JButton moveDownButton = new JButton("Down");
        moveDownButton.addActionListener(e -> move(1));

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        for (JButton button : List.of(moveUpButton, moveDownButton)) {
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
            buttonPanel.add(button);
        }

        JScrollPane listScroll = new JScrollPane(myList);
        listScroll.setPreferredSize(new Dimension(160, 0));
        JPanel listPanel = new JPanel(new BorderLayout(4, 0));
        listPanel.add(listScroll, BorderLayout.CENTER);
        listPanel.add(buttonPanel, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createTitledBorder("Columns"));
        panel.add(listPanel, BorderLayout.WEST);
        panel.add(myDetailPanel, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(Integer.MAX_VALUE, PANEL_HEIGHT));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, PANEL_HEIGHT));
        return panel;
    }

    private void toggleVisibilityIfOnCheckBox(MouseEvent e) {
        int index = myList.locationToIndex(e.getPoint());
        if (index < 0) {
            return;
        }
        Rectangle cellBounds = myList.getCellBounds(index, index);
        if (cellBounds == null || e.getX() - cellBounds.x >= CHECKBOX_HIT_WIDTH) {
            return;
        }
        ColumnRow row = myRows.get(index);
        row.myVisible = !row.myVisible;
        // Re-setting the element is what makes the list repaint that row.
        myListModel.set(index, row);
        updateDetail();
    }

    private void move(int offset) {
        int index = myList.getSelectedIndex();
        int target = index + offset;
        if (index < 0 || target < 0 || target >= myRows.size()) {
            return;
        }
        myRows.add(target, myRows.remove(index));
        rebuildListModel();
        myList.setSelectedIndex(target);
    }

    /** Shows the selected column's style controls; blank when nothing is selected. */
    private void updateDetail() {
        myDetailPanel.removeAll();

        int index = myList.getSelectedIndex();
        if (index >= 0 && index < myRows.size()) {
            myDetailPanel.add(buildDetailGrid(myRows.get(index).myStyleEditor), BorderLayout.CENTER);
        }
        myDetailPanel.revalidate();
        myDetailPanel.repaint();
    }

    private static JPanel buildDetailGrid(ComponentStyleEditor editor) {
        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = JBUI.insets(2, 4);

        addDetailRow(grid, gbc, 0, "Font:", editor.getFontCombo());
        addDetailRow(grid, gbc, 1, "Size:", editor.getSizeSpinner());
        addDetailRow(grid, gbc, 2, "Style:", editor.getStyleCombo());
        addDetailRow(grid, gbc, 3, "Color:", editor.getColorButton());
        return grid;
    }

    private static void addDetailRow(JPanel grid, GridBagConstraints gbc, int row, String label,
                                     JComponent field) {
        gbc.gridy = row;
        gbc.gridx = 0;
        grid.add(new JBLabel(label), gbc);
        gbc.gridx = 1;
        grid.add(field, gbc);
    }

    private void rebuildListModel() {
        myListModel.clear();
        for (ColumnRow row : myRows) {
            myListModel.addElement(row);
        }
    }

    public void resetFrom(List<ColumnConfig> columns) {
        myRows.clear();
        for (ColumnConfig column : columns) {
            myRows.add(new ColumnRow(column.id, column.visible,
                new ComponentStyleEditor(column.id, column.style)));
        }
        rebuildListModel();
        if (!myRows.isEmpty() && myList.getSelectedIndex() < 0) {
            myList.setSelectedIndex(0);
        }
        updateDetail();
    }

    public void applyTo(TurtleCommanderSettings.State settings) {
        settings.columns.clear();
        for (ColumnRow row : myRows) {
            ColumnConfig config = new ColumnConfig();
            config.id = row.myId;
            config.visible = row.myVisible;
            row.myStyleEditor.applyTo(config.style);
            settings.columns.add(config);
        }
    }

    public boolean isModified(List<ColumnConfig> savedColumns) {
        if (myRows.size() != savedColumns.size()) {
            return true;
        }
        for (int i = 0; i < myRows.size(); i++) {
            ColumnRow row = myRows.get(i);
            ColumnConfig saved = savedColumns.get(i);
            if (!row.myId.equals(saved.id) || row.myVisible != saved.visible
                || row.myStyleEditor.isModified(saved.style)) {
                return true;
            }
        }
        return false;
    }

    /** Renders each row as a checkbox labelled with the column id. */
    private static final class CheckBoxRenderer implements ListCellRenderer<ColumnRow> {
        private final JCheckBox myCheckBox = new JCheckBox();

        @Override
        public Component getListCellRendererComponent(JList<? extends ColumnRow> list, ColumnRow value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            myCheckBox.setText(value != null ? value.myId : "");
            myCheckBox.setSelected(value != null && value.myVisible);
            myCheckBox.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            myCheckBox.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            myCheckBox.setOpaque(true);
            myCheckBox.setFont(list.getFont());
            return myCheckBox;
        }
    }
}
