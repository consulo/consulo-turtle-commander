package consulo.turtlecommander.settings;

import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.Alerts;
import consulo.ui.Button;
import consulo.ui.ButtonStyle;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.IntBox;
import consulo.ui.Label;
import consulo.ui.Space;
import consulo.ui.StaticPosition;
import consulo.ui.Table;
import consulo.ui.TextAttribute;
import consulo.ui.TextBox;
import consulo.ui.TextEffect;
import consulo.ui.TextItemPresentation;
import consulo.ui.WidthAndHeight;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.color.ColorValue;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.ex.dialog.DialogValue;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.font.Font;
import consulo.ui.font.FontManager;
import consulo.ui.image.Image;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.TableLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Edits one colourisation rule: its identity, its matchers, and the style it applies. Matchers are edited through
 * {@link ColorMatcherEditDialogDescriptor}; the colour pickers are the same ones the theme editor uses, so the
 * settings surface stays consistent.
 */
public final class ColorRuleEditDialogDescriptor extends DialogDescriptor {
    /** The "Default" entry of the font-style combo, meaning "don't override". */
    private static final int FONT_STYLE_DEFAULT_INDEX = 0;
    private static final List<String> FONT_STYLE_LABELS = List.of("Default", "Plain", "Bold", "Italic", "Bold Italic");

    /**
     * A matcher in the table. Matchers are records, so two identical ones would be one item to the table; the
     * wrapper keeps every row distinct.
     */
    private static final class MatcherRow {
        RuleMatcher matcher;

        MatcherRow(RuleMatcher matcher) {
            this.matcher = matcher;
        }
    }

    /** The single preview row; a fresh instance per refresh makes the table render it again. */
    private static final class PreviewRow {
        final String text;

        PreviewRow(String text) {
            this.text = text;
        }
    }

    /** Every input of the dialog; made in {@link #createCenterComponent}, on the UI thread of the dialog. */
    private static final class Form {
        final TextBox name = TextBox.create("").withVisibleLength(24);
        final IntBox priority = IntBox.create(0).withRange(-1_000_000, 1_000_000);
        final CheckBox active = CheckBox.create(LocalizeValue.localizeTODO("Active"), true);
        final ComboBox<Combinator> combinator = ComboBox.create(Combinator.values());

        final MutableFlatDataModel<MatcherRow> matcherModel = FlatDataModel.of(List.of());
        final Table<MatcherRow> matcherTable = Table.create(matcherModel);

        final UnifiedColorPicker fontColor = new UnifiedColorPicker();
        final UnifiedColorPicker backgroundColor = new UnifiedColorPicker();
        final UnifiedColorPicker dotColor = new UnifiedColorPicker();
        final UnifiedIconPicker icon = new UnifiedIconPicker();
        final ComboBox<String> fontStyle = ComboBox.create(FONT_STYLE_LABELS);
        final IntBox fontSize = IntBox.create(0).withRange(0, 72);
        final CheckBox strikethrough = CheckBox.create(LocalizeValue.localizeTODO("Strikethrough"), false);

        final MutableFlatDataModel<PreviewRow> previewModel = FlatDataModel.of(List.of());
        final Table<PreviewRow> preview = Table.create(previewModel);
        /** The table font, so a size override keeps the family the panel draws with. */
        @Nullable Font previewBaseFont;

        @RequiredUIAccess
        Form() {
        }
    }

    private final @Nullable ColorRule myInitial;
    private final String myId;

    private @Nullable Form myForm;
    private @Nullable ColorRule myResult;

    public ColorRuleEditDialogDescriptor(@Nullable ColorRule initial) {
        super(LocalizeValue.localizeTODO(initial == null ? "Add color rule" : "Edit color rule"));
        myInitial = initial;
        myId = initial != null ? initial.id() : UUID.randomUUID().toString();
    }

    /** Populated on OK; null when the dialog was cancelled. */
    public @Nullable ColorRule getResult() {
        return myResult;
    }

    // --- Layout -------------------------------------------------------------------------------------

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        Form form = new Form();
        myForm = form;

        form.combinator.setTextRenderer(value -> LocalizeValue.localizeTODO(value == Combinator.OR
            ? "Any matcher matches (OR)"
            : "All matchers must match (AND)"));
        configureMatcherTable(form);
        configurePreview(form);

        loadFrom(form, myInitial);
        wireLivePreview(form);

        VerticalLayout root = VerticalLayout.create(Space.MEDIUM);
        root.add(buildHeader(form));
        root.add(LabeledLayout.create(LocalizeValue.localizeTODO("Matchers"), buildMatchersPanel(form)));
        root.add(LabeledLayout.create(LocalizeValue.localizeTODO("Style"), buildStylePanel(form)));
        root.add(LabeledLayout.create(LocalizeValue.localizeTODO("Preview"), form.preview));
        return root;
    }

    @RequiredUIAccess
    private static Component buildHeader(Form form) {
        HorizontalLayout priorityRow = HorizontalLayout.create(Space.LARGE);
        priorityRow.add(form.priority);
        priorityRow.add(form.active);

        TableLayout header = TableLayout.create(StaticPosition.TOP);
        header.add(Label.create(LocalizeValue.localizeTODO("Name:")), TableLayout.cell(0, 0));
        header.add(form.name, TableLayout.cell(0, 1).fill());
        header.add(Label.create(LocalizeValue.localizeTODO("Priority:")), TableLayout.cell(1, 0));
        header.add(priorityRow, TableLayout.cell(1, 1));
        header.add(Label.create(LocalizeValue.localizeTODO("Combine with:")), TableLayout.cell(2, 0));
        header.add(form.combinator, TableLayout.cell(2, 1));
        return header;
    }

    @RequiredUIAccess
    private Component buildMatchersPanel(Form form) {
        HorizontalLayout toolbar = HorizontalLayout.create(Space.SMALL);
        toolbar.add(toolbarButton("Add...", PlatformIconGroup.generalAdd(), () -> addMatcher(form)));
        toolbar.add(toolbarButton("Edit...", PlatformIconGroup.actionsEdit(), () -> editSelectedMatcher(form)));
        toolbar.add(toolbarButton("Delete", PlatformIconGroup.generalRemove(), () -> deleteSelectedMatcher(form)));
        return DockLayout.create(Space.SMALL).top(toolbar).center(ScrollableLayout.create(form.matcherTable));
    }

    @RequiredUIAccess
    static Button toolbarButton(String tooltip, Image icon, Runnable action) {
        Button button = Button.create(LocalizeValue.empty());
        button.setIcon(icon);
        button.addStyle(ButtonStyle.TOOLBAR);
        button.setToolTipText(LocalizeValue.localizeTODO(tooltip));
        button.addClickListener(event -> action.run());
        return button;
    }

    @RequiredUIAccess
    private static Component buildStylePanel(Form form) {
        TableLayout panel = TableLayout.create(StaticPosition.TOP);
        addStyleRow(panel, 0, "Font color:", form.fontColor.getComponent());
        addStyleRow(panel, 1, "Background:", form.backgroundColor.getComponent());
        addStyleRow(panel, 2, "Icon dot:", form.dotColor.getComponent());
        addStyleRow(panel, 3, "Icon:", withHint(form.icon.getComponent(), "(replaces the file-type icon)"));
        addStyleRow(panel, 4, "Font style:", form.fontStyle);
        addStyleRow(panel, 5, "Font size override:", withHint(form.fontSize, "(0 = no override)"));
        addStyleRow(panel, 6, "Effects:", form.strikethrough);
        return panel;
    }

    @RequiredUIAccess
    private static void addStyleRow(TableLayout panel, int row, String label, Component field) {
        panel.add(Label.create(LocalizeValue.localizeTODO(label)), TableLayout.cell(row, 0));
        panel.add(field, TableLayout.cell(row, 1));
    }

    @RequiredUIAccess
    private static Component withHint(Component field, String hint) {
        HorizontalLayout row = HorizontalLayout.create(Space.SMALL);
        row.add(field);
        row.add(Label.create(LocalizeValue.localizeTODO(hint)));
        return row;
    }

    @RequiredUIAccess
    private void configureMatcherTable(Form form) {
        form.matcherTable.addColumn(LocalizeValue.localizeTODO("Type"), (MatcherRow row) -> typeNameOf(row.matcher))
            .setWidth(120);
        form.matcherTable.addColumn(LocalizeValue.localizeTODO("Summary"),
                (MatcherRow row) -> RuleMatcherDescriptions.describe(row.matcher))
            .setWidth(420);
        form.matcherTable.addDoubleClickListener(event -> {
            if (event.getValue() != null) {
                editSelectedMatcher(form);
            }
        });
    }

    @Override
    public @Nullable WidthAndHeight getInitialSize() {
        // in font sizes: header and preview about ten, the matcher table about twelve, the style rows about sixteen
        return WidthAndHeight.ofFont(50, 46);
    }

    // --- Live preview ------------------------------------------------------------------------------

    @RequiredUIAccess
    private static void configurePreview(Form form) {
        form.preview.setShowHeader(false);
        form.preview.setRowBackgroundGetter(row -> toColorValue(form.backgroundColor.getColor()));
        form.preview.addColumn(LocalizeValue.empty(), (PreviewRow row) -> row.text)
            // the table font is only read here, so a size override can keep its family
            .setFontBuilder(font -> {
                form.previewBaseFont = font;
                return font;
            })
            .setRenderer((presentation, value, row) -> renderPreview(form, presentation, row));
        // the preview is a picture of a row, not something to select: a selection band would hide its background
        form.preview.addSelectListener(event -> {
            if (event.getValue() != null) {
                form.preview.deselectAll();
            }
        });
    }

    @RequiredUIAccess
    private static void wireLivePreview(Form form) {
        form.fontColor.setOnChange(() -> refreshPreview(form));
        form.backgroundColor.setOnChange(() -> refreshPreview(form));
        form.dotColor.setOnChange(() -> refreshPreview(form));
        form.icon.setOnChange(() -> refreshPreview(form));
        form.fontStyle.addValueListener(event -> refreshPreview(form));
        form.fontSize.addValueListener(event -> refreshPreview(form));
        form.strikethrough.addValueListener(event -> refreshPreview(form));
        form.name.addValueListener(event -> refreshPreview(form));
        refreshPreview(form);
    }

    @RequiredUIAccess
    private static void refreshPreview(Form form) {
        String name = text(form.name);
        form.previewModel.replaceAll(List.of(new PreviewRow(name.isBlank() ? "Preview" : name)));
    }

    /** Renders the row exactly as a matching row would appear in the panel. */
    @RequiredUIAccess
    private static void renderPreview(Form form, TextItemPresentation presentation, PreviewRow row) {
        Integer fontStyle = fontStyleOfIndex(fontStyleIndex(form));
        int style = fontStyle != null ? fontStyle : TextAttribute.STYLE_PLAIN;

        TextAttribute attribute = new TextAttribute(style, toColorValue(form.fontColor.getColor()));
        if (Boolean.TRUE.equals(form.strikethrough.getValue())) {
            attribute = attribute.withEffect(TextEffect.STRIKEOUT);
        }

        int sizeOverride = number(form.fontSize);
        Font base = form.previewBaseFont;
        if (sizeOverride > 0 && base != null) {
            presentation.withFont(FontManager.get().createFont(base.getFamily(), sizeOverride, style));
        }
        presentation.withIcon(RuleIcons.resolve(form.icon.getIconId()));
        presentation.append(row.text, attribute);
    }

    private static @Nullable ColorValue toColorValue(@Nullable Color color) {
        return color != null ? TargetAWT.from(color) : null;
    }

    // --- Loading and saving -------------------------------------------------------------------------

    @RequiredUIAccess
    private static void loadFrom(Form form, @Nullable ColorRule rule) {
        if (rule == null) {
            form.name.setValue("");
            form.priority.setValue(0);
            form.active.setValue(true);
            form.combinator.setValue(Combinator.AND);
            form.icon.setIconId("");
            form.fontStyle.setValueByIndex(FONT_STYLE_DEFAULT_INDEX);
            form.strikethrough.setValue(false);
            return;
        }

        form.name.setValue(rule.name());
        form.priority.setValue(rule.priority());
        form.active.setValue(rule.active());
        form.combinator.setValue(rule.combinator());
        List<MatcherRow> rows = new ArrayList<>();
        for (RuleMatcher matcher : rule.matchers()) {
            rows.add(new MatcherRow(matcher));
        }
        form.matcherModel.replaceAll(rows);

        RuleStyle style = rule.style();
        form.fontColor.setColor(PresetColors.decode(style.fontColor()));
        form.backgroundColor.setColor(PresetColors.decode(style.backgroundColor()));
        form.dotColor.setColor(PresetColors.decode(style.iconDotColor()));
        form.icon.setIconId(style.iconId());
        form.fontStyle.setValueByIndex(fontStyleIndexOf(style.fontStyle()));
        form.fontSize.setValue(style.fontSize() != null ? style.fontSize() : 0);
        form.strikethrough.setValue(Boolean.TRUE.equals(style.strikethrough()));
    }

    private static int fontStyleIndex(Form form) {
        int index = FONT_STYLE_LABELS.indexOf(form.fontStyle.getValue());
        return index >= 0 ? index : FONT_STYLE_DEFAULT_INDEX;
    }

    private static int fontStyleIndexOf(@Nullable Integer fontStyle) {
        if (fontStyle == null) {
            return FONT_STYLE_DEFAULT_INDEX;
        }
        return switch (fontStyle) {
            case Font.PLAIN -> 1;
            case Font.BOLD -> 2;
            case Font.ITALIC -> 3;
            case Font.BOLD | Font.ITALIC -> 4;
            default -> FONT_STYLE_DEFAULT_INDEX;
        };
    }

    /** The stored style uses the AWT constants, which the unified font constants share. */
    private static @Nullable Integer fontStyleOfIndex(int index) {
        return switch (index) {
            case 1 -> Font.PLAIN;
            case 2 -> Font.BOLD;
            case 3 -> Font.ITALIC;
            case 4 -> Font.BOLD | Font.ITALIC;
            default -> null;
        };
    }

    /** A blank name or a rule without matchers keeps the dialog open, with the reason. */
    @RequiredUIAccess
    @Override
    public boolean canHandle(AnAction action, @Nullable DialogValue value) {
        Form form = myForm;
        if (!(action instanceof DialogOkAction) || form == null) {
            return true;
        }
        String name = text(form.name).trim();
        if (name.isEmpty()) {
            Alerts.okError(LocalizeValue.localizeTODO("Name cannot be empty")).showAsync();
            return false;
        }
        if (form.matcherModel.getSize() == 0) {
            // a rule with no matchers would never fire, so it is almost certainly a mistake
            Alerts.okError(LocalizeValue.localizeTODO("Add at least one matcher")).showAsync();
            return false;
        }

        int fontSize = number(form.fontSize);
        Combinator combinator = form.combinator.getValue();
        myResult = new ColorRule(myId, name,
            number(form.priority),
            Boolean.TRUE.equals(form.active.getValue()),
            combinator != null ? combinator : Combinator.AND,
            matchers(form),
            new RuleStyle(
                form.fontColor.getColorHex(),
                form.backgroundColor.getColorHex(),
                form.dotColor.getColorHex(),
                fontStyleOfIndex(fontStyleIndex(form)),
                fontSize > 0 ? fontSize : null,
                Boolean.TRUE.equals(form.strikethrough.getValue()) ? Boolean.TRUE : null,
                form.icon.getIconId()));
        return true;
    }

    @RequiredUIAccess
    @Override
    public @Nullable Component getPreferredFocusedComponent() {
        Form form = myForm;
        return form != null ? form.name : super.getPreferredFocusedComponent();
    }

    // --- Matcher list --------------------------------------------------------------------------------

    private static List<RuleMatcher> matchers(Form form) {
        List<RuleMatcher> matchers = new ArrayList<>(form.matcherModel.getSize());
        for (MatcherRow row : form.matcherModel) {
            matchers.add(row.matcher);
        }
        return List.copyOf(matchers);
    }

    @RequiredUIAccess
    private static void addMatcher(Form form) {
        ColorMatcherEditDialogDescriptor descriptor = new ColorMatcherEditDialogDescriptor(null);
        showMatcherDialog(form, descriptor, () -> {
            RuleMatcher result = descriptor.getResult();
            if (result != null) {
                MatcherRow row = new MatcherRow(result);
                form.matcherModel.add(row);
                form.matcherTable.select(row);
            }
        });
    }

    @RequiredUIAccess
    private static void editSelectedMatcher(Form form) {
        MatcherRow selected = form.matcherTable.getSelectedItem();
        if (selected == null) {
            return;
        }
        ColorMatcherEditDialogDescriptor descriptor = new ColorMatcherEditDialogDescriptor(selected.matcher);
        showMatcherDialog(form, descriptor, () -> {
            RuleMatcher result = descriptor.getResult();
            if (result != null && form.matcherModel.indexOf(selected) >= 0) {
                selected.matcher = result;
                form.matcherModel.update(selected);
            }
        });
    }

    @RequiredUIAccess
    private static void deleteSelectedMatcher(Form form) {
        MatcherRow selected = form.matcherTable.getSelectedItem();
        if (selected != null) {
            form.matcherModel.remove(selected);
        }
    }

    /** The matcher dialog opens over this one; a cancelled dialog completes exceptionally and changes nothing. */
    @RequiredUIAccess
    private static void showMatcherDialog(Form form, ColorMatcherEditDialogDescriptor descriptor, Runnable onOk) {
        Application.get().getInstance(DialogService.class).build(form.matcherTable, descriptor).showAsync()
            .whenComplete((value, error) -> {
                if (error == null) {
                    onOk.run();
                }
            });
    }

    static String typeNameOf(RuleMatcher matcher) {
        return switch (matcher) {
            case RuleMatcher.Size ignored -> "Size";
            case RuleMatcher.Name ignored -> "Name";
            case RuleMatcher.Contains ignored -> "Contains";
            case RuleMatcher.Date date -> date.field() == DateField.CREATED ? "Created" : "Modified";
            case RuleMatcher.Text text -> switch (text.field()) {
                case OWNER -> "Owner";
                case GROUP -> "Group";
                case PERMISSIONS -> "Permissions";
            };
            case RuleMatcher.Symlink ignored -> "Symlink";
            case RuleMatcher.NamedPipe ignored -> "Named pipe";
        };
    }

    // --- Helpers -------------------------------------------------------------------------------------

    private static String text(TextBox box) {
        String value = box.getValue();
        return value != null ? value : "";
    }

    /** An IntBox mid-edit can hold no number; it then counts as zero, the "no override" value. */
    private static int number(IntBox box) {
        Integer value = box.getValue();
        return value != null ? value : 0;
    }
}
