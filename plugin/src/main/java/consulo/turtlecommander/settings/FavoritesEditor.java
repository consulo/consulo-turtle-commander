package consulo.turtlecommander.settings;

import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.IdeaFileChooser;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.turtlecommander.service.FavoriteEntry;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FavoriteIcons;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.SimpleColoredComponent;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/**
 * The favourites list with add/remove, reordering, and per-entry colour and icon.
 *
 * <p>Favourites are stored per project, so this edits the first open project's list — which is the
 * one whose settings dialog the user is in.
 */
public class FavoritesEditor {
    private static final int PANEL_HEIGHT = 260;
    private static final int VISIBLE_ROWS = 6;

    private final DefaultListModel<FavoriteEntry> myListModel = new DefaultListModel<>();
    private final JBList<FavoriteEntry> myList = new JBList<>(myListModel);
    private final JPanel myPanel;

    public FavoritesEditor() {
        loadFavorites();
        myList.setVisibleRowCount(VISIBLE_ROWS);
        myList.setCellRenderer(new FavoriteListCellRenderer());
        myPanel = buildPanel();
    }

    public JPanel getPanel() {
        return myPanel;
    }

    private JPanel buildPanel() {
        JButton addButton = new JButton("Add");
        addButton.addActionListener(e -> addFavorite());

        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> {
            int index = myList.getSelectedIndex();
            if (index >= 0) {
                myListModel.remove(index);
            }
        });

        JButton moveUpButton = new JButton("Up");
        moveUpButton.addActionListener(e -> move(-1));
        JButton moveDownButton = new JButton("Down");
        moveDownButton.addActionListener(e -> move(1));

        JButton colorButton = new JButton("Color...");
        colorButton.addActionListener(e -> chooseColor(colorButton));
        JButton iconButton = new JButton("Icon...");
        iconButton.addActionListener(e -> chooseIcon(iconButton));

        List<JButton> buttons =
            List.of(addButton, removeButton, moveUpButton, moveDownButton, colorButton, iconButton);
        for (JButton button : buttons) {
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
        }

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(Box.createVerticalStrut(8));
        buttonPanel.add(moveUpButton);
        buttonPanel.add(moveDownButton);
        buttonPanel.add(Box.createVerticalStrut(8));
        buttonPanel.add(colorButton);
        buttonPanel.add(iconButton);

        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createTitledBorder("Favorites"));
        panel.add(new JScrollPane(myList), BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.EAST);
        panel.setPreferredSize(new Dimension(Integer.MAX_VALUE, PANEL_HEIGHT));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, PANEL_HEIGHT));
        return panel;
    }

    private void addFavorite() {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setTitle("Add Favorite");
        descriptor.setDescription("Select a directory to add to favorites");

        VirtualFile chosen = IdeaFileChooser.chooseFile(descriptor, currentProject(), null);
        if (chosen != null) {
            myListModel.addElement(new FavoriteEntry(chosen.getPath(), "", ""));
        }
    }

    private void move(int offset) {
        int index = myList.getSelectedIndex();
        int target = index + offset;
        if (index < 0 || target < 0 || target >= myListModel.size()) {
            return;
        }
        myListModel.add(target, myListModel.remove(index));
        myList.setSelectedIndex(target);
    }

    private void chooseColor(JComponent anchor) {
        int index = myList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        FavoriteEntry entry = myListModel.getElementAt(index);
        ColorPickerButton.showColorPopup(anchor, "Choose Favorite Color",
            PresetColors.decode(entry.color), picked -> {
                myListModel.set(index,
                    new FavoriteEntry(entry.path, PresetColors.toHex(picked), entry.icon));
                myList.repaint();
            });
    }

    private void chooseIcon(JComponent anchor) {
        int index = myList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        FavoriteEntry entry = myListModel.getElementAt(index);
        IconPickerButton.showIconChooserPopup(anchor, key -> {
            myListModel.set(index, new FavoriteEntry(entry.path, entry.color, key));
            myList.repaint();
        });
    }

    public boolean isModified() {
        FileManagerStateService stateService = stateService();
        if (stateService == null) {
            return false;
        }
        List<FavoriteEntry> saved = stateService.getFavoriteEntries();
        if (myListModel.size() != saved.size()) {
            return true;
        }
        for (int i = 0; i < myListModel.size(); i++) {
            FavoriteEntry current = myListModel.getElementAt(i);
            FavoriteEntry savedEntry = saved.get(i);
            if (!current.path.equals(savedEntry.path)
                || !current.color.equals(savedEntry.color)
                || !current.icon.equals(savedEntry.icon)) {
                return true;
            }
        }
        return false;
    }

    public void apply() {
        FileManagerStateService stateService = stateService();
        if (stateService == null) {
            return;
        }
        List<FavoriteEntry> entries = new ArrayList<>(myListModel.size());
        for (int i = 0; i < myListModel.size(); i++) {
            entries.add(myListModel.getElementAt(i));
        }
        stateService.setFavoriteEntries(entries);
    }

    public void reset() {
        myListModel.clear();
        loadFavorites();
    }

    private void loadFavorites() {
        FileManagerStateService stateService = stateService();
        if (stateService == null) {
            return;
        }
        // Copied rather than shared, so cancelling the dialog leaves the stored list untouched.
        for (FavoriteEntry entry : stateService.getFavoriteEntries()) {
            myListModel.addElement(new FavoriteEntry(entry.path, entry.color, entry.icon));
        }
    }

    private static @Nullable FileManagerStateService stateService() {
        Project project = currentProject();
        return project != null ? FileManagerStateService.getInstance(project) : null;
    }

    private static @Nullable Project currentProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        return projects.length > 0 ? projects[0] : null;
    }

    /** Each row shows the favourite's icon — colour tint or chosen glyph — beside its path. */
    private static final class FavoriteListCellRenderer implements ListCellRenderer<FavoriteEntry> {
        private final SimpleColoredComponent myDelegate = new SimpleColoredComponent();

        @Override
        public Component getListCellRendererComponent(JList<? extends FavoriteEntry> list,
                                                      @Nullable FavoriteEntry value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            myDelegate.clear();
            myDelegate.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            myDelegate.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            myDelegate.setOpaque(true);
            if (value != null) {
                myDelegate.setIcon(FavoriteIcons.favoriteIcon(value.color, value.icon));
                myDelegate.append(value.path);
            }
            return myDelegate;
        }
    }
}
