package consulo.turtlecommander.settings;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ApplicationConfigurable;
import consulo.configurable.ConfigurationException;
import consulo.configurable.SearchableConfigurable;
import consulo.configurable.StandardConfigurableIds;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.turtlecommander.action.OpenPluginSettingsAction;
import consulo.turtlecommander.dialog.OverwritePolicyRenderer;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.service.OverwritePolicy;
import consulo.turtlecommander.service.ThumbnailCache;
import consulo.turtlecommander.ui.ViewMode;
import consulo.turtlecommander.util.FileUtils;
import consulo.ui.Button;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.Space;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.VerticalLayout;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** The plugin's settings page. */
@ExtensionImpl
public class TurtleCommanderConfigurable implements SearchableConfigurable, ApplicationConfigurable {
    /** A fixed instant so the format preview is stable while the user types a pattern. */
    private static final String SAMPLE_INSTANT = "2023-11-14T22:13:20Z";

    private @Nullable CheckBox myHighlightingCheckBox;
    private @Nullable CheckBox myCommandBarCheckBox;
    private @Nullable CheckBox mySaveCommandHistoryCheckBox;
    private @Nullable CheckBox myHideDriveSelectorCheckBox;
    private @Nullable CheckBox myHideStatusBarCheckBox;
    private @Nullable CheckBox myDeleteToRecycleBinCheckBox;
    private @Nullable CheckBox mySortWithDirectoriesCheckBox;
    private @Nullable CheckBox myNaturalNameSortCheckBox;
    private @Nullable CheckBox myCalculateDirectorySizeCheckBox;
    private @Nullable CheckBox myOpenDirectoriesWithSingleClickCheckBox;

    private @Nullable ComboBox<OverwritePolicy> myOverwritePolicyCombo;
    private @Nullable ComboBox<ViewMode> myDefaultViewModeCombo;
    private @Nullable ComboBox<PanelLayout> myPanelLayoutCombo;
    private @Nullable ComboBox<ThumbnailSize> myThumbnailSizeCombo;
    private @Nullable ComboBox<FileSizeFormat> myFileSizeFormatCombo;

    private @Nullable TextBox myDateTimeFormatField;
    private @Nullable TextBox myExportDateTimeFormatField;
    private @Nullable Label myDateTimePreviewLabel;

    private @Nullable AppearanceEditor myAppearanceEditor;
    private @Nullable ColumnsEditor myColumnsEditor;
    private @Nullable FavoritesEditor myFavoritesEditor;
    private @Nullable ColorRulesEditor myColorRulesEditor;

    @Override
    public String getId() {
        return OpenPluginSettingsAction.CONFIGURABLE_ID;
    }

    @Override
    public @Nullable String getParentId() {
        return StandardConfigurableIds.EXECUTION_GROUP;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Turtle Commander");
    }

    @RequiredUIAccess
    @Override
    public Component createUIComponent(@Nullable Disposable parentDisposable) {
        TurtleCommanderSettings.State settings = TurtleCommanderSettings.getInstance().getState();

        myHighlightingCheckBox = CheckBox.create(
            LocalizeValue.localizeTODO("Enable file colors and icons"), settings.enableFileNameHighlighting);
        myCommandBarCheckBox = CheckBox.create(
            LocalizeValue.localizeTODO("Show command bar (F5 Copy, F6 Move, etc.)"), settings.showCommandBar);
        mySaveCommandHistoryCheckBox = CheckBox.create(
            LocalizeValue.localizeTODO("Save command execution history"), settings.saveCommandHistory);
        myHideDriveSelectorCheckBox =
            CheckBox.create(LocalizeValue.localizeTODO("Hide drive selector"), settings.hideDriveSelector);
        myHideStatusBarCheckBox =
            CheckBox.create(LocalizeValue.localizeTODO("Hide status bar"), settings.hideStatusBar);
        myDeleteToRecycleBinCheckBox = CheckBox.create(
            LocalizeValue.localizeTODO("Delete to Recycle Bin (Shift+Delete bypasses)"), settings.deleteToRecycleBin);
        mySortWithDirectoriesCheckBox = CheckBox.create(
            LocalizeValue.localizeTODO("Sort directories together with files"), settings.sortWithDirectories);
        myNaturalNameSortCheckBox = CheckBox.create(
            LocalizeValue.localizeTODO("Natural sort order for file names (file2 before file10)"),
            settings.naturalNameSort);
        myCalculateDirectorySizeCheckBox = CheckBox.create(
            LocalizeValue.localizeTODO("Calculate directory size on selection"), settings.calculateDirectorySize);
        myOpenDirectoriesWithSingleClickCheckBox = CheckBox.create(
            LocalizeValue.localizeTODO("Open directories with a single click"),
            settings.openDirectoriesWithSingleClick);

        myOverwritePolicyCombo = ComboBox.create(OverwritePolicy.values());
        myOverwritePolicyCombo.setTextRenderer(policy -> LocalizeValue.of(OverwritePolicyRenderer.label(policy)));
        myOverwritePolicyCombo.setValue(TurtleCommanderSettings.getInstance().getDefaultOverwritePolicy());

        myDefaultViewModeCombo = ComboBox.create(ViewMode.values());
        myDefaultViewModeCombo.setTextRenderer(mode -> mode == null ? LocalizeValue.empty() : viewModeLabel(mode));
        myDefaultViewModeCombo.setValue(ViewMode.fromName(settings.defaultViewMode));

        myPanelLayoutCombo = ComboBox.create(PanelLayout.values());
        myPanelLayoutCombo.setTextRenderer(layout -> layout == null ? LocalizeValue.empty() : layoutLabel(layout));
        myPanelLayoutCombo.setValue(PanelLayout.fromName(settings.panelLayout));

        myThumbnailSizeCombo = ComboBox.create(ThumbnailSize.values());
        myThumbnailSizeCombo.setTextRenderer(
            size -> size == null ? LocalizeValue.empty() : LocalizeValue.localizeTODO(size.getLabel()));
        myThumbnailSizeCombo.setValue(ThumbnailSize.fromName(settings.thumbnailSize));

        myFileSizeFormatCombo = ComboBox.create(FileSizeFormat.values());
        myFileSizeFormatCombo.setTextRenderer(
            format -> format == null ? LocalizeValue.empty() : LocalizeValue.localizeTODO(format.getLabel()));
        myFileSizeFormatCombo.setValue(FileSizeFormat.fromName(settings.fileSizeFormat));

        buildDateTimeFields(settings);

        myAppearanceEditor = new AppearanceEditor(settings);
        myColumnsEditor = new ColumnsEditor(TurtleCommanderSettings.getInstance().getEffectiveColumns());
        myFavoritesEditor = new FavoritesEditor();
        myColorRulesEditor = new ColorRulesEditor(null);
        myColorRulesEditor.resetFrom(settings);

        return buildRoot();
    }

    @RequiredUIAccess
    private void buildDateTimeFields(TurtleCommanderSettings.State settings) {
        myDateTimePreviewLabel = Label.create();
        myDateTimeFormatField = TextBox.create(settings.dateTimeFormat.isBlank()
            ? TurtleCommanderSettings.DEFAULT_DATE_TIME_FORMAT : settings.dateTimeFormat).withVisibleLength(20);
        myDateTimeFormatField.setToolTipText(LocalizeValue.localizeTODO(
            "Java DateTimeFormatter pattern, e.g. yyyy-MM-dd HH:mm, dd/MM/yyyy, MMM d yyyy h:mm a"));
        // the value listener fires per keystroke, so the preview follows the typing
        myDateTimeFormatField.addValueListener(event -> updateDateTimePreview());
        updateDateTimePreview();

        myExportDateTimeFormatField = TextBox.create(settings.exportDateTimeFormat).withVisibleLength(20);
        myExportDateTimeFormatField.setToolTipText(LocalizeValue.localizeTODO("Date/time format for CSV/JSON "
            + "export. Leave blank for ISO-8601 UTC (e.g. 2026-06-16T12:34:56Z); otherwise a Java "
            + "DateTimeFormatter pattern in local time."));
    }

    @RequiredUIAccess
    private Component buildRoot() {
        VerticalLayout inner = VerticalLayout.create(Space.SMALL);

        inner.add(myHighlightingCheckBox);
        inner.add(myCommandBarCheckBox);
        inner.add(buildCommandHistoryRow());
        inner.add(myHideDriveSelectorCheckBox);
        inner.add(myHideStatusBarCheckBox);
        inner.add(myDeleteToRecycleBinCheckBox);
        inner.add(mySortWithDirectoriesCheckBox);
        inner.add(myNaturalNameSortCheckBox);
        inner.add(myCalculateDirectorySizeCheckBox);
        inner.add(myOpenDirectoriesWithSingleClickCheckBox);

        inner.add(labelledRow("Default copy/move policy:", myOverwritePolicyCombo));
        inner.add(labelledRow("Default tab view:", myDefaultViewModeCombo));
        inner.add(labelledRow("Layout:", myPanelLayoutCombo));
        inner.add(buildThumbnailRow());
        inner.add(labelledRow("File size format:", myFileSizeFormatCombo));
        inner.add(buildDateTimeRow());
        inner.add(buildExportDateTimeRow());

        inner.add(myAppearanceEditor.getComponent());
        inner.add(myColumnsEditor.getComponent());
        inner.add(myColorRulesEditor.getComponent());
        inner.add(myFavoritesEditor.getComponent());

        // The page is taller than the settings dialog, and the web settings editor does not scroll
        // configurable content by itself.
        return ScrollableLayout.create(inner);
    }

    @RequiredUIAccess
    private static HorizontalLayout labelledRow(String label, Component field) {
        HorizontalLayout row = HorizontalLayout.create(Space.SMALL);
        row.add(Label.create(LocalizeValue.localizeTODO(label)));
        row.add(field);
        return row;
    }

    @RequiredUIAccess
    private Component buildCommandHistoryRow() {
        Button clearButton = Button.create(LocalizeValue.localizeTODO("Clear"), event -> {
            // History is per project, so every open project's is cleared.
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                FileManagerStateService.getInstance(project).clearCommandHistory();
            }
        });
        clearButton.setToolTipText(LocalizeValue.localizeTODO("Remove all saved command-line history"));

        HorizontalLayout row = HorizontalLayout.create(Space.MEDIUM);
        row.add(mySaveCommandHistoryCheckBox);
        row.add(clearButton);
        return row;
    }

    @RequiredUIAccess
    private Component buildThumbnailRow() {
        ThumbnailCache cache = ThumbnailCache.getInstance();
        Label cacheSizeLabel = Label.create(LocalizeValue.of(FileUtils.formatSize(cache.getCacheSize())));
        Button clearCacheButton = Button.create(LocalizeValue.localizeTODO("Clear"), event -> {
            cache.clearCache();
            cacheSizeLabel.setText(LocalizeValue.of(FileUtils.formatSize(cache.getCacheSize())));
        });

        HorizontalLayout row = labelledRow("Thumbnail size:", myThumbnailSizeCombo);
        // the larger gap separates the cache controls from the size choice they do not belong to
        HorizontalLayout cacheGroup = HorizontalLayout.create(Space.SMALL);
        cacheGroup.add(Label.create(LocalizeValue.localizeTODO("Cache:")));
        cacheGroup.add(cacheSizeLabel);
        cacheGroup.add(clearCacheButton);

        HorizontalLayout outer = HorizontalLayout.create(Space.LARGE);
        outer.add(row);
        outer.add(cacheGroup);
        return outer;
    }

    @RequiredUIAccess
    private Component buildDateTimeRow() {
        HorizontalLayout row = labelledRow("Date/time format:", myDateTimeFormatField);
        row.add(myDateTimePreviewLabel);
        return row;
    }

    @RequiredUIAccess
    private Component buildExportDateTimeRow() {
        HorizontalLayout row = labelledRow("Export date/time format:", myExportDateTimeFormatField);
        row.add(Label.create(LocalizeValue.localizeTODO("(blank = ISO-8601 UTC)")));
        return row;
    }

    /**
     * Renders a sample timestamp beside the pattern field, so the user sees what the format does —
     * and an inline error for a bad pattern — instead of finding out after Apply.
     */
    @RequiredUIAccess
    private void updateDateTimePreview() {
        if (myDateTimePreviewLabel == null || myDateTimeFormatField == null) {
            return;
        }
        String pattern = textOf(myDateTimeFormatField);
        if (pattern.isEmpty()) {
            pattern = TurtleCommanderSettings.DEFAULT_DATE_TIME_FORMAT;
        }
        String text;
        try {
            text = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault())
                .format(Instant.parse(SAMPLE_INSTANT));
        }
        catch (Exception e) {
            text = "invalid pattern";
        }
        myDateTimePreviewLabel.setText(LocalizeValue.of("→ " + text));
    }

    // --- Configurable contract -------------------------------------------------------------------------

    @RequiredUIAccess
    @Override
    public boolean isModified() {
        TurtleCommanderSettings.State settings = TurtleCommanderSettings.getInstance().getState();
        return isChecked(myHighlightingCheckBox) != settings.enableFileNameHighlighting
            || isChecked(myCommandBarCheckBox) != settings.showCommandBar
            || isChecked(mySaveCommandHistoryCheckBox) != settings.saveCommandHistory
            || isChecked(myHideDriveSelectorCheckBox) != settings.hideDriveSelector
            || isChecked(myHideStatusBarCheckBox) != settings.hideStatusBar
            || isChecked(myDeleteToRecycleBinCheckBox) != settings.deleteToRecycleBin
            || isChecked(mySortWithDirectoriesCheckBox) != settings.sortWithDirectories
            || isChecked(myNaturalNameSortCheckBox) != settings.naturalNameSort
            || isChecked(myCalculateDirectorySizeCheckBox) != settings.calculateDirectorySize
            || isChecked(myOpenDirectoriesWithSingleClickCheckBox) != settings.openDirectoriesWithSingleClick
            || !selectedOverwritePolicy().name().equals(settings.defaultOverwritePolicy)
            || !selectedViewMode().equals(settings.defaultViewMode)
            || !selectedPanelLayout().equals(settings.panelLayout)
            || !selectedThumbnailSize().equals(settings.thumbnailSize)
            || !selectedFileSizeFormat().equals(settings.fileSizeFormat)
            || !enteredDateTimeFormat().equals(settings.dateTimeFormat)
            || !enteredExportDateTimeFormat().equals(settings.exportDateTimeFormat)
            || myAppearanceEditor.isModified(settings)
            || myColumnsEditor.isModified(TurtleCommanderSettings.getInstance().getEffectiveColumns())
            || myFavoritesEditor.isModified()
            || myColorRulesEditor.isModified();
    }

    @RequiredUIAccess
    @Override
    public void apply() throws ConfigurationException {
        TurtleCommanderSettings service = TurtleCommanderSettings.getInstance();
        TurtleCommanderSettings.State settings = service.getState();

        settings.enableFileNameHighlighting = isChecked(myHighlightingCheckBox);
        settings.showCommandBar = isChecked(myCommandBarCheckBox);
        settings.saveCommandHistory = isChecked(mySaveCommandHistoryCheckBox);
        settings.hideDriveSelector = isChecked(myHideDriveSelectorCheckBox);
        settings.hideStatusBar = isChecked(myHideStatusBarCheckBox);
        settings.deleteToRecycleBin = isChecked(myDeleteToRecycleBinCheckBox);
        settings.sortWithDirectories = isChecked(mySortWithDirectoriesCheckBox);
        settings.naturalNameSort = isChecked(myNaturalNameSortCheckBox);
        settings.calculateDirectorySize = isChecked(myCalculateDirectorySizeCheckBox);
        settings.openDirectoriesWithSingleClick = isChecked(myOpenDirectoriesWithSingleClickCheckBox);

        settings.defaultOverwritePolicy = selectedOverwritePolicy().name();
        settings.defaultViewMode = selectedViewMode();
        settings.panelLayout = selectedPanelLayout();
        settings.thumbnailSize = selectedThumbnailSize();
        settings.fileSizeFormat = selectedFileSizeFormat();
        settings.dateTimeFormat = enteredDateTimeFormat();
        settings.exportDateTimeFormat = enteredExportDateTimeFormat();

        myAppearanceEditor.applyTo(settings);
        myColumnsEditor.applyTo(settings);
        myColorRulesEditor.applyTo(settings);

        // The panel and tab fonts also live in the older top-level fields; keep them in step so a
        // downgrade — or any code still reading them — sees the same values.
        settings.panelFontFamily = settings.styles.panelStyle.fontFamily;
        settings.panelFontSize = settings.styles.panelStyle.fontSize;
        settings.tabFontFamily = settings.styles.tabStyle.fontFamily;
        settings.tabFontSize = settings.styles.tabStyle.fontSize;

        service.fireSettingsChanged();
        // Favourites are project state rather than settings state, so they save separately.
        myFavoritesEditor.apply();
    }

    @RequiredUIAccess
    @Override
    public void reset() {
        TurtleCommanderSettings.State settings = TurtleCommanderSettings.getInstance().getState();

        myHighlightingCheckBox.setValue(settings.enableFileNameHighlighting);
        myCommandBarCheckBox.setValue(settings.showCommandBar);
        mySaveCommandHistoryCheckBox.setValue(settings.saveCommandHistory);
        myHideDriveSelectorCheckBox.setValue(settings.hideDriveSelector);
        myHideStatusBarCheckBox.setValue(settings.hideStatusBar);
        myDeleteToRecycleBinCheckBox.setValue(settings.deleteToRecycleBin);
        mySortWithDirectoriesCheckBox.setValue(settings.sortWithDirectories);
        myNaturalNameSortCheckBox.setValue(settings.naturalNameSort);
        myCalculateDirectorySizeCheckBox.setValue(settings.calculateDirectorySize);
        myOpenDirectoriesWithSingleClickCheckBox.setValue(settings.openDirectoriesWithSingleClick);

        myOverwritePolicyCombo.setValue(
            TurtleCommanderSettings.getInstance().getDefaultOverwritePolicy());
        myDefaultViewModeCombo.setValue(ViewMode.fromName(settings.defaultViewMode));
        myPanelLayoutCombo.setValue(PanelLayout.fromName(settings.panelLayout));
        myThumbnailSizeCombo.setValue(ThumbnailSize.fromName(settings.thumbnailSize));
        myFileSizeFormatCombo.setValue(FileSizeFormat.fromName(settings.fileSizeFormat));

        myDateTimeFormatField.setValue(settings.dateTimeFormat.isBlank()
            ? TurtleCommanderSettings.DEFAULT_DATE_TIME_FORMAT : settings.dateTimeFormat);
        myExportDateTimeFormatField.setValue(settings.exportDateTimeFormat);
        updateDateTimePreview();

        myAppearanceEditor.resetFrom(settings);
        myColumnsEditor.resetFrom(TurtleCommanderSettings.getInstance().getEffectiveColumns());
        myFavoritesEditor.reset();
        myColorRulesEditor.resetFrom(settings);
    }

    @RequiredUIAccess
    @Override
    public void disposeUIResources() {
        myHighlightingCheckBox = null;
        myCommandBarCheckBox = null;
        mySaveCommandHistoryCheckBox = null;
        myHideDriveSelectorCheckBox = null;
        myHideStatusBarCheckBox = null;
        myDeleteToRecycleBinCheckBox = null;
        mySortWithDirectoriesCheckBox = null;
        myNaturalNameSortCheckBox = null;
        myCalculateDirectorySizeCheckBox = null;
        myOpenDirectoriesWithSingleClickCheckBox = null;
        myOverwritePolicyCombo = null;
        myDefaultViewModeCombo = null;
        myPanelLayoutCombo = null;
        myThumbnailSizeCombo = null;
        myFileSizeFormatCombo = null;
        myDateTimeFormatField = null;
        myExportDateTimeFormatField = null;
        myDateTimePreviewLabel = null;
        myAppearanceEditor = null;
        myColumnsEditor = null;
        myFavoritesEditor = null;
        myColorRulesEditor = null;
    }

    // --- Reading the controls ---------------------------------------------------------------------------

    private static boolean isChecked(CheckBox checkBox) {
        return Boolean.TRUE.equals(checkBox.getValue());
    }

    /** Trimmed, with a cleared box (null value) read as blank. */
    private static String textOf(TextBox textBox) {
        String text = textBox.getValue();
        return text == null ? "" : text.trim();
    }

    private OverwritePolicy selectedOverwritePolicy() {
        OverwritePolicy policy = myOverwritePolicyCombo.getValue();
        return policy != null ? policy : OverwritePolicy.ASK;
    }

    private String selectedViewMode() {
        ViewMode mode = myDefaultViewModeCombo.getValue();
        return (mode != null ? mode : ViewMode.TABLE).name();
    }

    private static LocalizeValue viewModeLabel(ViewMode mode) {
        return LocalizeValue.localizeTODO(switch (mode) {
            case TABLE -> "Table";
            case LIST -> "List";
            case THUMBNAIL -> "Thumbnail";
            case TREE -> "Tree";
        });
    }

    private String selectedPanelLayout() {
        PanelLayout layout = myPanelLayoutCombo.getValue();
        return (layout != null ? layout : PanelLayout.HORIZONTAL).name();
    }

    private static LocalizeValue layoutLabel(PanelLayout layout) {
        return LocalizeValue.localizeTODO(switch (layout) {
            case HORIZONTAL -> "Horizontal Dual-panel";
            case VERTICAL -> "Vertical Dual-panel";
            case SINGLE -> "Single-panel";
        });
    }

    private String selectedThumbnailSize() {
        ThumbnailSize size = myThumbnailSizeCombo.getValue();
        return (size != null ? size : ThumbnailSize.SMALL).name();
    }

    private String selectedFileSizeFormat() {
        FileSizeFormat format = myFileSizeFormatCombo.getValue();
        return (format != null ? format : FileSizeFormat.AUTO_BINARY).name();
    }

    private String enteredDateTimeFormat() {
        String text = textOf(myDateTimeFormatField);
        return text.isEmpty() ? TurtleCommanderSettings.DEFAULT_DATE_TIME_FORMAT : text;
    }

    /** Blank is meaningful here — it selects the ISO-8601 UTC default — so it is kept verbatim. */
    private String enteredExportDateTimeFormat() {
        return textOf(myExportDateTimeFormatField);
    }
}
