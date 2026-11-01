package consulo.turtlecommander.settings;

import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserFactory;
import consulo.localize.LocalizeValue;
import consulo.ui.Button;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.InputBoxBuilder;
import consulo.ui.Label;
import consulo.ui.ListBox;
import consulo.ui.MessageBoxes;
import consulo.ui.Separator;
import consulo.ui.Space;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Appearance section: a theme selector over a per-component style editor.
 *
 * <p>Picking a theme overwrites the style editors. The styles in effect before the first theme was
 * applied are stashed in {@code preThemeStyles}, so switching back to Default restores what the user
 * had rather than blanking everything.
 */
public class AppearanceEditor {
    private static final int PANEL_HEIGHT = 280;
    private static final int LIST_WIDTH = 160;

    private final Map<StyledComponent, ComponentStyleEditor> myEditors =
        new EnumMap<>(StyledComponent.class);
    private final MutableFlatDataModel<Theme> myThemeModel = FlatDataModel.of(List.of());
    private final ComboBox<Theme> myThemeCombo;
    private final ListBox<StyledComponent> myComponentList;
    private final Component myComponent;

    /** Guards the theme combo's listener while the combo is being repopulated programmatically. */
    private boolean mySuppressThemeAction;
    private Theme myPreviousTheme = Theme.DEFAULT;

    @RequiredUIAccess
    public AppearanceEditor(TurtleCommanderSettings.State settings) {
        for (StyledComponent component : StyledComponent.values()) {
            myEditors.put(component, new ComponentStyleEditor(component.getLabel(),
                component.styleIn(settings.styles),
                legacyFamily(component, settings), legacySize(component, settings))
                .withSections(sectionsOf(component)));
        }

        myComponentList = ListBox.create(StyledComponent.values());
        myComponentList.setRenderer((presentation, item) -> {
            StyledComponent value = item.getValue();
            presentation.append(value != null ? value.getLabel() : "");
        });

        myThemeCombo = ComboBox.create(myThemeModel);
        myThemeCombo.setTextRenderer(theme -> theme != null ? LocalizeValue.of(theme.name()) : LocalizeValue.empty());
        rebuildThemeCombo(settings);
        myThemeCombo.addValueListener(event -> onThemeSelected());

        myComponent = buildPanel();
        myComponentList.addValueListener(event -> renderDetail());
        myComponentList.setValueByIndex(0);
        renderDetail();
    }

    public Component getComponent() {
        return myComponent;
    }

    public ComponentStyleEditor editorFor(StyledComponent component) {
        return myEditors.get(component);
    }

    public String getSelectedThemeName() {
        Theme selected = myThemeCombo.getValue();
        return selected != null ? selected.name() : "";
    }

    /**
     * Which rows a component gets depends on what it can actually show: the command bar only has a
     * background, and only the file panel has selection colours.
     */
    private static EnumSet<ComponentStyleEditor.Section> sectionsOf(StyledComponent component) {
        return switch (component) {
            case COMMAND_BAR -> EnumSet.of(ComponentStyleEditor.Section.BACKGROUND);
            case PANEL -> EnumSet.allOf(ComponentStyleEditor.Section.class);
            default -> EnumSet.of(ComponentStyleEditor.Section.FONT, ComponentStyleEditor.Section.BACKGROUND);
        };
    }

    @RequiredUIAccess
    private Component buildPanel() {
        ScrollableLayout listScroll = ScrollableLayout.create(myComponentList);
        listScroll.setWidth(LIST_WIDTH);

        // Every form lives in the stack for good and only its visibility follows the selection:
        // re-parenting the controls on each selection is what the Swing version did, and not every frontend
        // takes that well.
        VerticalLayout detailStack = VerticalLayout.create(Space.NONE);
        for (StyledComponent component : StyledComponent.values()) {
            detailStack.add(myEditors.get(component).getComponent());
        }

        DockLayout styleArea = DockLayout.create(Space.MEDIUM);
        styleArea.left(listScroll).center(detailStack);
        styleArea.setHeight(PANEL_HEIGHT);

        DockLayout content = DockLayout.create(Space.SMALL);
        content.top(buildThemeRow()).center(styleArea);
        return LabeledLayout.create(LocalizeValue.localizeTODO("Appearance"), content);
    }

    @RequiredUIAccess
    private Component buildThemeRow() {
        HorizontalLayout row = HorizontalLayout.create(Space.X_SMALL);
        row.add(Label.create(LocalizeValue.localizeTODO("Theme:")));
        row.add(myThemeCombo);
        row.add(themeButton("Save", "Save current styles as a new theme", this::saveCurrentTheme));
        row.add(themeButton("Rename", "Rename the selected custom theme", this::renameSelectedTheme));
        row.add(themeButton("Delete", "Delete the selected custom theme", this::deleteSelectedTheme));
        row.add(Separator.vertical());
        row.add(themeButton("Export", "Export the selected theme to a file", this::exportSelectedTheme));
        row.add(themeButton("Import", "Import a theme from a file", this::importThemeFromFile));
        row.add(Separator.vertical());
        row.add(themeButton("Reset to Defaults", null, this::resetStyles));
        return row;
    }

    @RequiredUIAccess
    private static Button themeButton(String text, @Nullable String tooltip, @RequiredUIAccess Runnable action) {
        Button button = Button.create(LocalizeValue.localizeTODO(text));
        if (tooltip != null) {
            button.setToolTipText(LocalizeValue.localizeTODO(tooltip));
        }
        button.addClickListener(event -> action.run());
        return button;
    }

    // --- Detail form ---------------------------------------------------------------------------------

    /** Shows only the selected component's form. */
    @RequiredUIAccess
    private void renderDetail() {
        StyledComponent selected = myComponentList.getValue();
        for (Map.Entry<StyledComponent, ComponentStyleEditor> entry : myEditors.entrySet()) {
            entry.getValue().getComponent().setVisible(entry.getKey() == selected);
        }
    }

    // --- Themes ---------------------------------------------------------------------------------------

    @RequiredUIAccess
    private void onThemeSelected() {
        Theme theme = myThemeCombo.getValue();
        if (mySuppressThemeAction || theme == null) {
            return;
        }
        Theme previous = myPreviousTheme;
        myPreviousTheme = theme;
        applyThemeToEditors(theme, previous);
    }

    @RequiredUIAccess
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

    @RequiredUIAccess
    public void rebuildThemeCombo(TurtleCommanderSettings.State state) {
        String currentName = getSelectedThemeName();
        if (currentName.isEmpty()) {
            currentName = state.themeName.isEmpty() ? Theme.DEFAULT.name() : state.themeName;
        }
        List<Theme> allThemes = ThemeManager.getAllThemes(state);

        mySuppressThemeAction = true;
        try {
            myThemeModel.replaceAll(allThemes);
            Theme current = allThemes.get(indexOfTheme(allThemes, currentName));
            myThemeCombo.setValue(current, false);
            myPreviousTheme = current;
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

    @RequiredUIAccess
    private void saveCurrentTheme() {
        askThemeName("Save Theme", "Enter theme name:", "", name -> {
            TurtleCommanderSettings.State state = TurtleCommanderSettings.getInstance().getState();
            // An existing name is replaced in place, which is what "save over this theme" should do.
            ThemeManager.saveTheme(state, buildThemeFromEditors(name));
            rebuildThemeCombo(state);
            selectThemeByName(name);
        });
    }

    @RequiredUIAccess
    private void renameSelectedTheme() {
        String currentName = getSelectedThemeName();
        askThemeName("Rename Theme", "New name:", currentName, newName -> {
            TurtleCommanderSettings.State state = TurtleCommanderSettings.getInstance().getState();
            ThemeManager.RenameResult result = ThemeManager.renameTheme(state, currentName, newName);
            if (result != ThemeManager.RenameResult.SUCCESS) {
                showWarning(renameFailureMessage(currentName, newName, result), "Rename Theme");
                return;
            }
            rebuildThemeCombo(state);
            selectThemeByName(newName);
        });
    }

    /** Hands a confirmed, non-blank name to {@code onName}; a cancelled box does nothing, a blank one warns. */
    @RequiredUIAccess
    private void askThemeName(String title, String prompt, String initial, @RequiredUIAccess Consumer<String> onName) {
        InputBoxBuilder.text()
            .title(LocalizeValue.localizeTODO(title))
            .text(LocalizeValue.localizeTODO(prompt))
            .value(initial)
            .showAsync(myComponent)
            .whenComplete((name, error) -> {
                if (error != null || name == null) {
                    return;
                }
                if (name.isBlank()) {
                    showWarning("Theme name cannot be empty.", title);
                    return;
                }
                onName.accept(name);
            });
    }

    @RequiredUIAccess
    private void deleteSelectedTheme() {
        String name = getSelectedThemeName();
        TurtleCommanderSettings.State state = TurtleCommanderSettings.getInstance().getState();
        if (!ThemeManager.deleteTheme(state, name)) {
            showWarning("\"" + name + "\" cannot be deleted.", "Delete Theme");
            return;
        }
        rebuildThemeCombo(state);
        selectThemeByName(Theme.DEFAULT.name());
    }

    @RequiredUIAccess
    private void exportSelectedTheme() {
        Theme theme = myThemeCombo.getValue();
        if (theme == null) {
            return;
        }
        FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        descriptor.setTitle("Export Theme");
        descriptor.setDescription("Choose the directory to write the theme file into");
        FileChooserFactory.getInstance().newBuilder(descriptor).showSingle().whenComplete((directory, error) -> {
            if (directory == null) {
                return;
            }
            Path target = directory.resolve(theme.name().replace(' ', '_') + ".tctheme");
            try {
                Files.writeString(target, ThemeManager.exportTheme(theme), StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                showWarning("Failed to write " + target + ": " + e.getMessage(), "Export Theme");
            }
        });
    }

    @RequiredUIAccess
    private void importThemeFromFile() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
        descriptor.setTitle("Import Theme");
        descriptor.setDescription("Select a Turtle Commander theme file");
        FileChooserFactory.getInstance().newBuilder(descriptor).showSingle().whenComplete((chosen, error) -> {
            if (chosen == null) {
                return;
            }

            Theme theme;
            try {
                theme = ThemeManager.importTheme(
                    Files.readString(chosen, StandardCharsets.UTF_8));
            }
            catch (IOException e) {
                showWarning("Failed to read " + chosen + ": " + e.getMessage(), "Import Theme");
                return;
            }
            if (theme == null) {
                showWarning("Invalid theme file.", "Import Theme");
                return;
            }

            TurtleCommanderSettings.State state = TurtleCommanderSettings.getInstance().getState();
            if (ThemeManager.getAllThemes(state).stream().noneMatch(t -> t.name().equals(theme.name()))) {
                importTheme(state, theme, false);
                return;
            }
            // Replacing loses the existing theme, so ask; declining - or closing the box - imports under a
            // unique name.
            MessageBoxes.yesNo()
                .title(LocalizeValue.localizeTODO("Import Theme"))
                .text(LocalizeValue.localizeTODO("A theme named \"" + theme.name() + "\" already exists. Replace it?"))
                .showAsync(myComponent)
                .whenComplete((replace, replaceError) -> importTheme(state, theme, Boolean.TRUE.equals(replace)));
        });
    }

    @RequiredUIAccess
    private void importTheme(TurtleCommanderSettings.State state, Theme theme, boolean overwrite) {
        ThemeManager.ImportOutcome outcome = ThemeManager.importThemeToState(state, theme, overwrite);
        rebuildThemeCombo(state);
        selectThemeByName(outcome.name());
    }

    @RequiredUIAccess
    private void showWarning(String text, String title) {
        MessageBoxes.okWarning(LocalizeValue.localizeTODO(text))
            .title(LocalizeValue.localizeTODO(title))
            .showAsync(myComponent);
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

    @RequiredUIAccess
    private void selectThemeByName(String name) {
        for (Theme theme : myThemeModel) {
            if (theme.name().equals(name)) {
                myThemeCombo.setValue(theme);
                return;
            }
        }
    }

    /** Clears every style override and returns the theme selection to Default. */
    @RequiredUIAccess
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

    @RequiredUIAccess
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
}
