package consulo.turtlecommander.settings;

import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.IdeaFileChooser;
import consulo.turtlecommander.dialog.InputDialog;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.Messages;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Appearance section: a theme selector over a per-component style editor.
 *
 * <p>Picking a theme overwrites the style editors. The styles in effect before the first theme was
 * applied are stashed in {@code preThemeStyles}, so switching back to Default restores what the user
 * had rather than blanking everything.
 */
public class AppearanceEditor {
    private static final int PANEL_HEIGHT = 280;
    private static final int LABEL_COLUMN_WIDTH = 110;
    private static final int FIELD_COLUMN_WIDTH = 170;

    private final Map<StyledComponent, ComponentStyleEditor> myEditors =
        new EnumMap<>(StyledComponent.class);
    private final ComboBox<Theme> myThemeCombo = new ComboBox<>();
    private final JPanel myDetailPanel = new JPanel(new BorderLayout());
    private final JBList<StyledComponent> myComponentList;
    private final JPanel myPanel;

    /** Guards the theme combo's listener while the combo is being repopulated programmatically. */
    private boolean mySuppressThemeAction;
    private Theme myPreviousTheme = Theme.DEFAULT;

    public AppearanceEditor(TurtleCommanderSettings.State settings) {
        for (StyledComponent component : StyledComponent.values()) {
            myEditors.put(component, new ComponentStyleEditor(component.getLabel(),
                component.styleIn(settings.styles),
                legacyFamily(component, settings), legacySize(component, settings)));
        }

        DefaultListModel<StyledComponent> listModel = new DefaultListModel<>();
        for (StyledComponent component : StyledComponent.values()) {
            listModel.addElement(component);
        }
        myComponentList = new JBList<>(listModel);
        myComponentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        myComponentList.setCellRenderer(new ComponentLabelRenderer());
        myComponentList.addListSelectionListener(e -> renderDetail());

        rebuildThemeCombo(settings);
        myThemeCombo.addActionListener(e -> onThemeSelected());

        myPanel = buildPanel();
        myComponentList.setSelectedIndex(0);
    }

    public JPanel getPanel() {
        return myPanel;
    }

    public ComponentStyleEditor editorFor(StyledComponent component) {
        return myEditors.get(component);
    }

    public String getSelectedThemeName() {
        Object selected = myThemeCombo.getSelectedItem();
        return selected instanceof Theme theme ? theme.name() : "";
    }

    private JPanel buildPanel() {
        JScrollPane listScroll = new JScrollPane(myComponentList);
        listScroll.setPreferredSize(new Dimension(160, 0));

        JPanel styleArea = new JPanel(new BorderLayout(8, 0));
        styleArea.add(listScroll, BorderLayout.WEST);
        styleArea.add(myDetailPanel, BorderLayout.CENTER);
        styleArea.setPreferredSize(new Dimension(Integer.MAX_VALUE, PANEL_HEIGHT));
        styleArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, PANEL_HEIGHT));

        JPanel panel = new JPanel(new BorderLayout(8, 4));
        panel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createTitledBorder("Appearance"));
        panel.add(buildThemeRow(), BorderLayout.NORTH);
        panel.add(styleArea, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildThemeRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.add(new JBLabel("Theme:"));
        row.add(myThemeCombo);
        row.add(themeButton("Save", "Save current styles as a new theme", this::saveCurrentTheme));
        row.add(themeButton("Rename", "Rename the selected custom theme", this::renameSelectedTheme));
        row.add(themeButton("Delete", "Delete the selected custom theme", this::deleteSelectedTheme));
        row.add(separator());
        row.add(themeButton("Export", "Export the selected theme to a file", this::exportSelectedTheme));
        row.add(themeButton("Import", "Import a theme from a file", this::importThemeFromFile));
        row.add(separator());
        row.add(themeButton("Reset to Defaults", null, this::resetStyles));
        return row;
    }

    private static JButton themeButton(String text, @Nullable String tooltip, Runnable action) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.addActionListener(e -> action.run());
        return button;
    }

    private static JSeparator separator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(2, 24));
        return separator;
    }

    // --- Detail form ---------------------------------------------------------------------------------

    /**
     * The selected component's controls. Which rows appear depends on what the component can
     * actually show: the command bar only has a background, and only the file panel has selection
     * colours.
     */
    private void renderDetail() {
        myDetailPanel.removeAll();
        StyledComponent component = myComponentList.getSelectedValue();
        if (component != null) {
            myDetailPanel.add(buildDetailGrid(component), BorderLayout.NORTH);
        }
        myDetailPanel.revalidate();
        myDetailPanel.repaint();
    }

    private JPanel buildDetailGrid(StyledComponent component) {
        ComponentStyleEditor editor = myEditors.get(component);
        JPanel grid = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;

        // A spacer row pins the column widths so the form doesn't jump as the selection changes.
        gbc.insets = JBUI.insets(0, 4);
        gbc.gridy = 0;
        gbc.gridx = 0;
        grid.add(Box.createHorizontalStrut(JBUI.scale(LABEL_COLUMN_WIDTH)), gbc);
        gbc.gridx = 1;
        grid.add(Box.createHorizontalStrut(JBUI.scale(FIELD_COLUMN_WIDTH)), gbc);
        gbc.insets = JBUI.insets(2, 4);

        int row = 1;
        if (component != StyledComponent.COMMAND_BAR) {
            row = addRow(grid, gbc, row, "Font:", editor.getFontCombo());
            row = addRow(grid, gbc, row, "Size:", editor.getSizeSpinner());
            row = addRow(grid, gbc, row, "Style:", editor.getStyleCombo());
            row = addRow(grid, gbc, row, "Color:", editor.getColorButton());
        }
        row = addRow(grid, gbc, row, "Background:", editor.getBackgroundColorButton());
        if (component == StyledComponent.PANEL) {
            row = addRow(grid, gbc, row, "Selected:", editor.getSelectedColorButton());
            addRow(grid, gbc, row, "Active selected:", editor.getActiveSelectedColorButton());
        }
        return grid;
    }

    private static int addRow(JPanel grid, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridy = row;
        gbc.gridx = 0;
        grid.add(new JBLabel(label), gbc);
        gbc.gridx = 1;
        grid.add(field, gbc);
        return row + 1;
    }

    // --- Themes ---------------------------------------------------------------------------------------

    private void onThemeSelected() {
        if (mySuppressThemeAction || !(myThemeCombo.getSelectedItem() instanceof Theme theme)) {
            return;
        }
        Theme previous = myPreviousTheme;
        myPreviousTheme = theme;
        applyThemeToEditors(theme, previous);
    }

    private void applyThemeToEditors(Theme theme, Theme previousTheme) {
        TurtleCommanderSettings.State state = TurtleCommanderSettings.getInstance().getState();

        if (Theme.DEFAULT.name().equals(theme.name())) {
            for (StyledComponent component : StyledComponent.values()) {
                myEditors.get(component).resetFrom(component.styleIn(state.preThemeStyles));
            }
            return;
        }
        // Snapshot only on the way out of Default: otherwise hopping between themes would overwrite
        // the user's own styles with the previous theme's.
        if (Theme.DEFAULT.name().equals(previousTheme.name())) {
            snapshotEditorsToPreTheme(state);
        }
        for (StyledComponent component : StyledComponent.values()) {
            myEditors.get(component).resetFrom(component.styleIn(theme).toComponentStyle());
        }
    }

    private void snapshotEditorsToPreTheme(TurtleCommanderSettings.State state) {
        for (StyledComponent component : StyledComponent.values()) {
            myEditors.get(component).applyTo(component.styleIn(state.preThemeStyles));
        }
    }

    private Theme buildThemeFromEditors(String name) {
        return StyledComponent.toTheme(name, component -> {
            ComponentStyle style = new ComponentStyle();
            myEditors.get(component).applyTo(style);
            return style;
        });
    }

    public void rebuildThemeCombo(TurtleCommanderSettings.State state) {
        String currentName = getSelectedThemeName();
        if (currentName.isEmpty()) {
            currentName = state.themeName.isEmpty() ? Theme.DEFAULT.name() : state.themeName;
        }
        List<Theme> allThemes = ThemeManager.getAllThemes(state);

        mySuppressThemeAction = true;
        try {
            myThemeCombo.setModel(new DefaultComboBoxModel<>(allThemes.toArray(new Theme[0])));
            myThemeCombo.setSelectedIndex(indexOfTheme(allThemes, currentName));
            myPreviousTheme = allThemes.get(indexOfTheme(allThemes, currentName));
        }
        finally {
            mySuppressThemeAction = false;
        }
    }

    private static int indexOfTheme(List<Theme> themes, String name) {
        for (int i = 0; i < themes.size(); i++) {
            if (themes.get(i).name().equals(name)) {
                return i;
            }
        }
        return 0;
    }

    private void saveCurrentTheme() {
        InputDialog dialog = new InputDialog(null, "Save Theme", "Enter theme name:", "");
        if (!dialog.showAndGet()) {
            return;
        }
        String name = dialog.getInputValue();
        if (name.isBlank()) {
            Messages.showWarningDialog("Theme name cannot be empty.", "Save Theme");
            return;
        }
        TurtleCommanderSettings.State state = TurtleCommanderSettings.getInstance().getState();
        // An existing name is replaced in place, which is what "save over this theme" should do.
        ThemeManager.saveTheme(state, buildThemeFromEditors(name));
        rebuildThemeCombo(state);
        selectThemeByName(name);
    }

    private void renameSelectedTheme() {
        String currentName = getSelectedThemeName();
        TurtleCommanderSettings.State state = TurtleCommanderSettings.getInstance().getState();

        InputDialog dialog = new InputDialog(null, "Rename Theme", "New name:", currentName);
        if (!dialog.showAndGet()) {
            return;
        }
        String newName = dialog.getInputValue();
        if (newName.isBlank()) {
            Messages.showWarningDialog("Theme name cannot be empty.", "Rename Theme");
            return;
        }
        ThemeManager.RenameResult result = ThemeManager.renameTheme(state, currentName, newName);
        if (result != ThemeManager.RenameResult.SUCCESS) {
            Messages.showWarningDialog(renameFailureMessage(currentName, newName, result), "Rename Theme");
            return;
        }
        rebuildThemeCombo(state);
        selectThemeByName(newName);
    }

    private void deleteSelectedTheme() {
        String name = getSelectedThemeName();
        TurtleCommanderSettings.State state = TurtleCommanderSettings.getInstance().getState();
        if (!ThemeManager.deleteTheme(state, name)) {
            Messages.showWarningDialog("\"" + name + "\" cannot be deleted.", "Delete Theme");
            return;
        }
        rebuildThemeCombo(state);
        selectThemeByName(Theme.DEFAULT.name());
    }

    private void exportSelectedTheme() {
        Object selected = myThemeCombo.getSelectedItem();
        if (!(selected instanceof Theme theme)) {
            return;
        }
        FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        descriptor.setTitle("Export Theme");
        descriptor.setDescription("Choose the directory to write the theme file into");
        VirtualFile directory = IdeaFileChooser.chooseFile(descriptor, null, null);
        if (directory == null) {
            return;
        }
        Path target = Path.of(directory.getPath()).resolve(theme.name().replace(' ', '_') + ".tctheme");
        try {
            Files.writeString(target, ThemeManager.exportTheme(theme), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            Messages.showWarningDialog("Failed to write " + target + ": " + e.getMessage(), "Export Theme");
        }
    }

    private void importThemeFromFile() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
        descriptor.setTitle("Import Theme");
        descriptor.setDescription("Select a Turtle Commander theme file");
        VirtualFile chosen = IdeaFileChooser.chooseFile(descriptor, null, null);
        if (chosen == null) {
            return;
        }

        Theme theme;
        try {
            theme = ThemeManager.importTheme(
                Files.readString(Path.of(chosen.getPath()), StandardCharsets.UTF_8));
        }
        catch (IOException e) {
            Messages.showWarningDialog("Failed to read " + chosen.getPath() + ": " + e.getMessage(),
                "Import Theme");
            return;
        }
        if (theme == null) {
            Messages.showWarningDialog("Invalid theme file.", "Import Theme");
            return;
        }

        TurtleCommanderSettings.State state = TurtleCommanderSettings.getInstance().getState();
        boolean overwrite = false;
        if (ThemeManager.getAllThemes(state).stream().anyMatch(t -> t.name().equals(theme.name()))) {
            // Replacing loses the existing theme, so ask; declining imports under a unique name.
            overwrite = Messages.showYesNoDialog(
                "A theme named \"" + theme.name() + "\" already exists. Replace it?",
                "Import Theme", null) == Messages.YES;
        }
        ThemeManager.ImportOutcome outcome = ThemeManager.importThemeToState(state, theme, overwrite);
        rebuildThemeCombo(state);
        selectThemeByName(outcome.name());
    }

    private static String renameFailureMessage(String oldName, String newName,
                                               ThemeManager.RenameResult result) {
        return switch (result) {
            case CONFLICT -> "A theme named \"" + newName + "\" already exists.";
            case NOT_FOUND -> "\"" + oldName + "\" no longer exists.";
            case INVALID_NAME -> "\"" + newName + "\" is not a valid theme name.";
            case SUCCESS -> "";
        };
    }

    private void selectThemeByName(String name) {
        for (int i = 0; i < myThemeCombo.getItemCount(); i++) {
            if (myThemeCombo.getItemAt(i).name().equals(name)) {
                myThemeCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    /** Clears every style override and returns the theme selection to Default. */
    private void resetStyles() {
        for (ComponentStyleEditor editor : myEditors.values()) {
            editor.resetFrom(new ComponentStyle());
        }
        TurtleCommanderSettings.State state = TurtleCommanderSettings.getInstance().getState();
        state.preThemeStyles.reset();
        // Re-seed the built-ins if the user had deleted them all, so the combo is never empty.
        if (state.themes.isEmpty()) {
            for (Theme theme : Theme.INITIAL_THEMES) {
                state.themes.add(SavedTheme.fromTheme(theme));
            }
            rebuildThemeCombo(state);
        }
        mySuppressThemeAction = true;
        try {
            selectThemeByName(Theme.DEFAULT.name());
            myPreviousTheme = Theme.DEFAULT;
        }
        finally {
            mySuppressThemeAction = false;
        }
    }

    // --- Apply / reset / modified ------------------------------------------------------------------------

    public void applyTo(TurtleCommanderSettings.State settings) {
        for (StyledComponent component : StyledComponent.values()) {
            myEditors.get(component).applyTo(component.styleIn(settings.styles));
        }
        settings.themeName = getSelectedThemeName();
    }

    public void resetFrom(TurtleCommanderSettings.State settings) {
        for (StyledComponent component : StyledComponent.values()) {
            myEditors.get(component).resetFrom(component.styleIn(settings.styles),
                legacyFamily(component, settings), legacySize(component, settings));
        }
        rebuildThemeCombo(settings);
    }

    public boolean isModified(TurtleCommanderSettings.State settings) {
        for (StyledComponent component : StyledComponent.values()) {
            if (myEditors.get(component).isModified(component.styleIn(settings.styles),
                legacyFamily(component, settings), legacySize(component, settings))) {
                return true;
            }
        }
        String savedTheme = settings.themeName.isEmpty() ? Theme.DEFAULT.name() : settings.themeName;
        return !getSelectedThemeName().equals(savedTheme);
    }

    /**
     * The panel and tab fonts predate per-component styles, so an unset component style still falls
     * back to those older settings rather than to the look and feel.
     */
    private static String legacyFamily(StyledComponent component, TurtleCommanderSettings.State settings) {
        return switch (component) {
            case PANEL -> settings.panelFontFamily;
            case TAB -> settings.tabFontFamily;
            default -> "";
        };
    }

    private static int legacySize(StyledComponent component, TurtleCommanderSettings.State settings) {
        return switch (component) {
            case PANEL -> settings.panelFontSize;
            case TAB -> settings.tabFontSize;
            default -> 0;
        };
    }

    private static final class ComponentLabelRenderer implements ListCellRenderer<StyledComponent> {
        private final JBLabel myLabel = new JBLabel();

        ComponentLabelRenderer() {
            myLabel.setBorder(JBUI.Borders.empty(2, 6));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends StyledComponent> list,
                                                      @Nullable StyledComponent value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            myLabel.setText(value != null ? value.getLabel() : "");
            myLabel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            myLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            myLabel.setOpaque(true);
            myLabel.setFont(list.getFont());
            return myLabel;
        }
    }
}
