package consulo.turtlecommander.settings;

import consulo.application.Application;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserFactory;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.ComponentItemRenderer;
import consulo.ui.HorizontalAlignment;
import consulo.ui.InputBoxBuilder;
import consulo.ui.Label;
import consulo.ui.MessageBoxBuilder;
import consulo.ui.MessageBoxes;
import consulo.ui.MessageButtonRole;
import consulo.ui.Space;
import consulo.ui.Table;
import consulo.ui.TableItemEditor;
import consulo.ui.TextItemPresentation;
import consulo.ui.UIAccess;
import consulo.ui.ValueComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.image.Image;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Function;

/**
 * The rule table and its toolbar, embedded in the settings page.
 *
 * <p>Holds its own mutable copy of the rules so Apply/Cancel behave the way they do for themes and
 * columns: edits stay local until {@link #applyTo} writes them back.
 */
public class ColorRulesEditor {
    /**
     * Display and evaluation order: highest priority first — the winner in WINNER mode, the top
     * overlay in LAYERED — with ties broken by name so the order is stable across restarts.
     */
    private static final Comparator<ColorRule> BY_PRIORITY =
        Comparator.comparingInt(ColorRule::priority).reversed()
            .thenComparing(rule -> rule.name().toLowerCase(Locale.ROOT));

    private enum ImportChoice {
        REPLACE, APPEND, CANCEL
    }

    private final @Nullable Project myProject;

    private final List<ColorRule> myRules = new ArrayList<>();
    private List<ColorRule> mySavedSnapshot = List.of();
    private ColorizationMode mySavedMode = ColorizationMode.WINNER;

    private final MutableFlatDataModel<ColorRule> myTableModel = FlatDataModel.of(List.of());
    private final Table<ColorRule> myTable = Table.create(myTableModel);
    private final ComboBox<ColorizationMode> myModeCombo = ComboBox.create(ColorizationMode.values());
    private final Component myRoot;

    @RequiredUIAccess
    public ColorRulesEditor(@Nullable Project project) {
        myProject = project;

        myModeCombo.setTextRenderer(mode -> LocalizeValue.localizeTODO(mode == ColorizationMode.LAYERED
            ? "Layered (overlay by priority)"
            : "Winner takes all (highest priority)"));
        myModeCombo.setValue(ColorizationMode.WINNER);
        configureColumns();
        myTable.addDoubleClickListener(event -> {
            if (event.getValue() != null) {
                editSelected();
            }
        });

        DockLayout content = DockLayout.create(Space.SMALL);
        content.top(buildToolbar());
        content.center(ScrollableLayout.create(myTable));
        myRoot = LabeledLayout.create(LocalizeValue.localizeTODO("File colors and icons"), content);
    }

    public Component getComponent() {
        return myRoot;
    }

    @RequiredUIAccess
    public void resetFrom(TurtleCommanderSettings.State state) {
        myRules.clear();
        myRules.addAll(ColorRuleManager.getAllRules(state));
        myRules.sort(BY_PRIORITY);
        mySavedSnapshot = List.copyOf(myRules);
        mySavedMode = ColorRuleManager.getMode(state);
        myModeCombo.setValue(mySavedMode);
        myTableModel.replaceAll(myRules);
    }

    public void applyTo(TurtleCommanderSettings.State state) {
        state.colorRulesInitialized = true;
        state.colorRules.clear();
        for (ColorRule rule : myRules) {
            state.colorRules.add(SavedColorRule.fromRule(rule));
        }
        ColorRuleManager.setMode(state, selectedMode());
        mySavedSnapshot = List.copyOf(myRules);
        mySavedMode = selectedMode();
    }

    public boolean isModified() {
        return selectedMode() != mySavedMode || !myRules.equals(mySavedSnapshot);
    }

    private ColorizationMode selectedMode() {
        ColorizationMode mode = myModeCombo.getValue();
        return mode != null ? mode : ColorizationMode.WINNER;
    }

    // --- Toolbar ---------------------------------------------------------------------------------

    @RequiredUIAccess
    private Component buildToolbar() {
        HorizontalLayout buttons = HorizontalLayout.create(Space.X_SMALL);
        buttons.add(ColorRuleEditDialogDescriptor.toolbarButton("Add...", PlatformIconGroup.generalAdd(), this::addRule));
        buttons.add(ColorRuleEditDialogDescriptor.toolbarButton("Edit...", PlatformIconGroup.actionsEdit(), this::editSelected));
        buttons.add(ColorRuleEditDialogDescriptor.toolbarButton("Duplicate", PlatformIconGroup.actionsCopy(), this::duplicateSelected));
        buttons.add(ColorRuleEditDialogDescriptor.toolbarButton("Delete", PlatformIconGroup.generalRemove(), this::deleteSelected));
        buttons.add(ColorRuleEditDialogDescriptor.toolbarButton("Raise priority (move up)", PlatformIconGroup.actionsMoveup(),
            () -> moveSelected(-1)));
        buttons.add(ColorRuleEditDialogDescriptor.toolbarButton("Lower priority (move down)", PlatformIconGroup.actionsMovedown(),
            () -> moveSelected(1)));
        buttons.add(ColorRuleEditDialogDescriptor.toolbarButton("Import rules from a .tcrules file...",
            PlatformIconGroup.actionsImport(), this::importRules));
        buttons.add(ColorRuleEditDialogDescriptor.toolbarButton("Export current rules to a .tcrules file...",
            PlatformIconGroup.actionsExport(), this::exportCurrentRules));
        buttons.add(ColorRuleEditDialogDescriptor.toolbarButton("Reset to defaults", PlatformIconGroup.generalReset(),
            this::resetToDefaults));

        HorizontalLayout mode = HorizontalLayout.create(Space.SMALL);
        mode.add(Label.create(LocalizeValue.localizeTODO("Mode:")));
        mode.add(myModeCombo);

        return DockLayout.create(Space.MEDIUM).left(buttons).right(mode);
    }

    // --- Rule mutations ---------------------------------------------------------------------------

    @RequiredUIAccess
    private void addRule() {
        ColorRuleEditDialogDescriptor descriptor = new ColorRuleEditDialogDescriptor(null);
        showRuleDialog(descriptor, () -> {
            ColorRule rule = descriptor.getResult();
            if (rule != null) {
                myRules.add(rule);
                resortAndSelect(rule.id());
            }
        });
    }

    @RequiredUIAccess
    private void editSelected() {
        ColorRule selected = myTable.getSelectedItem();
        if (selected == null) {
            return;
        }
        ColorRuleEditDialogDescriptor descriptor = new ColorRuleEditDialogDescriptor(selected);
        showRuleDialog(descriptor, () -> {
            ColorRule rule = descriptor.getResult();
            // looked up again: the list may have changed while the dialog was open
            int index = indexOfId(selected.id());
            if (rule != null && index >= 0) {
                myRules.set(index, rule);
                resortAndSelect(rule.id());
            }
        });
    }

    /** A cancelled dialog completes exceptionally and changes nothing. */
    @RequiredUIAccess
    private void showRuleDialog(ColorRuleEditDialogDescriptor descriptor, Runnable onOk) {
        Application.get().getInstance(DialogService.class).build(myRoot, descriptor).showAsync()
            .whenComplete((value, error) -> {
                if (error == null) {
                    onOk.run();
                }
            });
    }

    @RequiredUIAccess
    private void duplicateSelected() {
        ColorRule source = myTable.getSelectedItem();
        if (source == null) {
            return;
        }
        ColorRule copy = new ColorRule(UUID.randomUUID().toString(), source.name() + " (copy)",
            source.priority(), source.active(), source.combinator(), source.matchers(), source.style());
        myRules.add(copy);
        resortAndSelect(copy.id());
    }

    @RequiredUIAccess
    private void deleteSelected() {
        ColorRule rule = myTable.getSelectedItem();
        if (rule == null) {
            return;
        }
        MessageBoxes.yesNo()
            .title(LocalizeValue.localizeTODO("Delete Rule"))
            .text(LocalizeValue.localizeTODO("Delete rule \"" + rule.name() + "\"?"))
            .showAsync(myRoot)
            .whenComplete((yes, error) -> {
                if (error == null && Boolean.TRUE.equals(yes) && myRules.removeIf(r -> r.id().equals(rule.id()))) {
                    resortAndSelect(null);
                }
            });
    }

    @RequiredUIAccess
    private void resetToDefaults() {
        MessageBoxes.yesNo()
            .title(LocalizeValue.localizeTODO("Reset Rules"))
            .text(LocalizeValue.localizeTODO("Replace all rules with the built-in defaults?"))
            .showAsync(myRoot)
            .whenComplete((yes, error) -> {
                if (error == null && Boolean.TRUE.equals(yes)) {
                    myRules.clear();
                    myRules.addAll(ColorRuleDefaults.builtinRules());
                    resortAndSelect(null);
                }
            });
    }

    /**
     * Swaps the selected rule with its neighbour in the priority order. The new priority is set just
     * past the neighbour's rather than nudged by one, so a single click always reorders even when
     * priorities are spaced far apart.
     */
    @RequiredUIAccess
    private void moveSelected(int direction) {
        ColorRule current = myTable.getSelectedItem();
        int index = current != null ? indexOfId(current.id()) : -1;
        int target = index + direction;
        if (current == null || index < 0 || target < 0 || target >= myRules.size()) {
            return;
        }
        ColorRule neighbour = myRules.get(target);
        int newPriority = direction < 0 ? neighbour.priority() + 1 : neighbour.priority() - 1;
        myRules.set(index, new ColorRule(current.id(), current.name(), newPriority, current.active(),
            current.combinator(), current.matchers(), current.style()));
        resortAndSelect(current.id());
    }

    @RequiredUIAccess
    private void setActive(ColorRule rule, boolean active) {
        int index = indexOfId(rule.id());
        if (index < 0 || myRules.get(index).active() == active) {
            return;
        }
        ColorRule current = myRules.get(index);
        myRules.set(index, new ColorRule(current.id(), current.name(), current.priority(), active,
            current.combinator(), current.matchers(), current.style()));
        resortAndSelect(current.id());
    }

    private int indexOfId(String id) {
        for (int i = 0; i < myRules.size(); i++) {
            if (myRules.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Restores the priority ordering after a mutation and re-selects the rule by id — its row moves
     * when its priority changes. The list stays sorted so the table always shows evaluation order.
     */
    @RequiredUIAccess
    private void resortAndSelect(@Nullable String selectId) {
        myRules.sort(BY_PRIORITY);
        myTableModel.replaceAll(myRules);
        if (selectId == null) {
            return;
        }
        int index = indexOfId(selectId);
        if (index >= 0) {
            ColorRule rule = myRules.get(index);
            myTable.select(rule);
            myTable.scrollTo(rule);
        }
    }

    // --- Import and export -------------------------------------------------------------------------

    @RequiredUIAccess
    private void exportCurrentRules() {
        InputBoxBuilder.text()
            .title(LocalizeValue.localizeTODO("Export Rules"))
            .text(LocalizeValue.localizeTODO("Config name:"))
            .value("")
            .showAsync()
            .whenComplete((name, error) -> {
                if (error != null || name == null) {
                    return;
                }
                if (name.isBlank()) {
                    warn("Config name cannot be empty.", "Export Rules");
                    return;
                }
                chooseExportDirectory(name);
            });
    }

    @RequiredUIAccess
    private void chooseExportDirectory(String name) {
        // the rules are captured now: the chooser is asynchronous and the table stays editable meanwhile
        List<ColorRule> rules = List.copyOf(myRules);
        UIAccess uiAccess = UIAccess.current();

        FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        descriptor.setTitle("Export Rules");
        descriptor.setDescription("Choose the directory to write the .tcrules file into");
        FileChooserFactory.getInstance().newBuilder(descriptor).project(myProject).showSingle().whenComplete((directory, error) -> {
            if (directory == null) {
                return;
            }
            String text = ColorRulesIO.export(new ColorRulesConfig(name, rules));
            Path target = directory.resolve(name.replace(' ', '_') + ".tcrules");
            try {
                Files.writeString(target, text, StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                uiAccess.give(() -> warn("Failed to write " + target + ": " + e.getMessage(), "Export Rules"));
            }
        });
    }

    @RequiredUIAccess
    private void importRules() {
        UIAccess uiAccess = UIAccess.current();

        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
        descriptor.setTitle("Import Rules");
        descriptor.setDescription("Select a Turtle Commander rules file");
        FileChooserFactory.getInstance().newBuilder(descriptor).project(myProject).showSingle().whenComplete((chosen, error) -> {
            if (chosen == null) {
                return;
            }

            ColorRulesConfig config;
            try {
                config = ColorRulesIO.parse(Files.readString(chosen, StandardCharsets.UTF_8));
            }
            catch (IOException e) {
                uiAccess.give(() -> warn("Failed to read " + chosen + ": " + e.getMessage(), "Import Rules"));
                return;
            }
            uiAccess.give(() -> {
                if (config == null) {
                    warn("Invalid rules file.", "Import Rules");
                    return;
                }
                askImportMode(config);
            });
        });
    }

    @RequiredUIAccess
    private void askImportMode(ColorRulesConfig config) {
        MessageBoxBuilder.<ImportChoice>create()
            .asQuestion()
            .title(LocalizeValue.localizeTODO("Import Rules"))
            .text(LocalizeValue.localizeTODO("Import \"" + config.name() + "\" (" + config.rules().size() + " rules)?"))
            .detail(LocalizeValue.localizeTODO("Replace the current rules, or append to them."))
            .button(MessageButtonRole.YES, LocalizeValue.localizeTODO("Replace"), ImportChoice.REPLACE)
            .asDefaultButton()
            .button(MessageButtonRole.NO, LocalizeValue.localizeTODO("Append"), ImportChoice.APPEND)
            .button(MessageButtonRole.CANCEL, LocalizeValue.localizeTODO("Cancel"), ImportChoice.CANCEL)
            .asExitButton()
            .showAsync(myRoot)
            .whenComplete((choice, error) -> {
                if (error != null || choice == null || choice == ImportChoice.CANCEL) {
                    return;
                }
                if (choice == ImportChoice.REPLACE) {
                    myRules.clear();
                }
                // fresh ids, so an imported rule can never collide with — or silently overwrite — an existing one
                for (ColorRule rule : config.rules()) {
                    myRules.add(ColorRuleDefaults.withId(rule, UUID.randomUUID().toString()));
                }
                resortAndSelect(null);
            });
    }

    @RequiredUIAccess
    private void warn(String message, String title) {
        MessageBoxes.okWarning(LocalizeValue.of(message)).title(LocalizeValue.localizeTODO(title)).showAsync(myRoot);
    }

    // --- Table -------------------------------------------------------------------------------------

    @RequiredUIAccess
    private void configureColumns() {
        myTable.addColumn(LocalizeValue.localizeTODO("On"), ColorRule::active)
            .setWidth(50)
            .setHorizontalAlignment(HorizontalAlignment.CENTER)
            .setRenderer(ComponentItemRenderer.reusable(
                () -> CheckBox.create(LocalizeValue.empty()),
                (checkBox, item) -> checkBox.setValue(Boolean.TRUE.equals(item.getValue()))))
            .setEditor(new TableItemEditor<>() {
                @RequiredUIAccess
                @Override
                public ValueComponent<Boolean> createComponent(ColorRule rule) {
                    return CheckBox.create(LocalizeValue.empty(), rule.active());
                }

                @RequiredUIAccess
                @Override
                public void commit(ColorRule rule, @Nullable Boolean value) {
                    setActive(rule, Boolean.TRUE.equals(value));
                }
            });
        myTable.addColumn(LocalizeValue.localizeTODO("Prio"), ColorRule::priority)
            .setWidth(60)
            .setHorizontalAlignment(HorizontalAlignment.CENTER);
        myTable.addColumn(LocalizeValue.localizeTODO("Name"), ColorRule::name)
            .setWidth(220);
        addColorColumn("Fg", rule -> rule.style().fontColor());
        addColorColumn("Bg", rule -> rule.style().backgroundColor());
        addColorColumn("Dot", rule -> rule.style().iconDotColor());
        myTable.addColumn(LocalizeValue.localizeTODO("Icon"), (ColorRule rule) -> rule.style().iconId())
            .setWidth(50)
            .setHorizontalAlignment(HorizontalAlignment.CENTER)
            .setRenderer((presentation, value, rule) -> renderIcon(presentation, rule.style().iconId()));
        myTable.addColumn(LocalizeValue.localizeTODO("Matchers"), ColorRulesEditor::summarizeMatchers)
            .setWidth(300);
    }

    /** Shows a rule's colour as a swatch; an unset colour renders as an empty cell. */
    @RequiredUIAccess
    private void addColorColumn(String header, Function<ColorRule, String> hexOf) {
        myTable.addColumn(LocalizeValue.localizeTODO(header), hexOf)
            .setWidth(50)
            .setHorizontalAlignment(HorizontalAlignment.CENTER)
            .setRenderer((presentation, value, rule) -> {
                String hex = hexOf.apply(rule);
                Color color = PresetColors.decode(hex);
                if (color != null) {
                    presentation.withIcon(ColorSwatches.swatch(color));
                    presentation.withTooltip(LocalizeValue.of(hex));
                }
            });
    }

    /** Shows a rule's override icon, or nothing when it doesn't set one. */
    private static void renderIcon(TextItemPresentation presentation, String id) {
        Image icon = RuleIcons.resolve(id);
        if (icon != null) {
            presentation.withIcon(icon);
        }
        String label = id.isEmpty() ? null : RuleIcons.label(id);
        if (label != null) {
            presentation.withTooltip(LocalizeValue.of(label));
        }
    }

    private static String summarizeMatchers(ColorRule rule) {
        if (rule.matchers().isEmpty()) {
            return "(none)";
        }
        StringJoiner joiner =
            new StringJoiner(rule.combinator() == Combinator.AND ? " AND " : " OR ");
        for (RuleMatcher matcher : rule.matchers()) {
            joiner.add(RuleMatcherDescriptions.describe(matcher));
        }
        return joiner.toString();
    }
}
