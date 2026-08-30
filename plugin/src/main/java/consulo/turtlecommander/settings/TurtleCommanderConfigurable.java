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
import consulo.turtlecommander.util.FileUtils;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBTextField;
import consulo.ui.ex.awt.event.DocumentAdapter;
import org.jspecify.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/** The plugin's settings page. */
@ExtensionImpl
public class TurtleCommanderConfigurable implements SearchableConfigurable, ApplicationConfigurable {
    /** A fixed instant so the format preview is stable while the user types a pattern. */
    private static final String SAMPLE_INSTANT = "2023-11-14T22:13:20Z";

    private static final String[] VIEW_MODE_LABELS = {"Table", "List", "Thumbnail", "Tree"};
    private static final String[] LAYOUT_LABELS =
        {"Horizontal Dual-panel", "Vertical Dual-panel", "Single-panel"};

    private @Nullable JCheckBox myHighlightingCheckBox;
    private @Nullable JCheckBox myCommandBarCheckBox;
    private @Nullable JCheckBox mySaveCommandHistoryCheckBox;
    private @Nullable JCheckBox myHideDriveSelectorCheckBox;
    private @Nullable JCheckBox myHideStatusBarCheckBox;
    private @Nullable JCheckBox myDeleteToRecycleBinCheckBox;
    private @Nullable JCheckBox mySortWithDirectoriesCheckBox;
    private @Nullable JCheckBox myNaturalNameSortCheckBox;
    private @Nullable JCheckBox myCalculateDirectorySizeCheckBox;
    private @Nullable JCheckBox myOpenDirectoriesWithSingleClickCheckBox;

    private @Nullable ComboBox<OverwritePolicy> myOverwritePolicyCombo;
    private @Nullable ComboBox<String> myDefaultViewModeCombo;
    private @Nullable ComboBox<String> myPanelLayoutCombo;
    private @Nullable ComboBox<ThumbnailSize> myThumbnailSizeCombo;
    private @Nullable ComboBox<FileSizeFormat> myFileSizeFormatCombo;

    private @Nullable JBTextField myDateTimeFormatField;
    private @Nullable JBTextField myExportDateTimeFormatField;
    private @Nullable JBLabel myDateTimePreviewLabel;

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
        // Consulo has no "tools" group, which is where this page sat upstream.
        return StandardConfigurableIds.PLATFORM_AND_PLUGINS_GROUP;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Turtle Commander");
    }

    @RequiredUIAccess
    @Override
    public JComponent createComponent(@Nullable Disposable parentDisposable) {
        TurtleCommanderSettings.State settings = TurtleCommanderSettings.getInstance().getState();

        myHighlightingCheckBox =
            new JCheckBox("Enable file colors and icons", settings.enableFileNameHighlighting);
        myCommandBarCheckBox =
            new JCheckBox("Show command bar (F5 Copy, F6 Move, etc.)", settings.showCommandBar);
        mySaveCommandHistoryCheckBox =
            new JCheckBox("Save command execution history", settings.saveCommandHistory);
        myHideDriveSelectorCheckBox = new JCheckBox("Hide drive selector", settings.hideDriveSelector);
        myHideStatusBarCheckBox = new JCheckBox("Hide status bar", settings.hideStatusBar);
        myDeleteToRecycleBinCheckBox =
            new JCheckBox("Delete to Recycle Bin (Shift+Delete bypasses)", settings.deleteToRecycleBin);
        mySortWithDirectoriesCheckBox =
            new JCheckBox("Sort directories together with files", settings.sortWithDirectories);
        myNaturalNameSortCheckBox = new JCheckBox(
            "Natural sort order for file names (file2 before file10)", settings.naturalNameSort);
        myCalculateDirectorySizeCheckBox =
            new JCheckBox("Calculate directory size on selection", settings.calculateDirectorySize);
        myOpenDirectoriesWithSingleClickCheckBox = new JCheckBox(
            "Open directories with a single click", settings.openDirectoriesWithSingleClick);

        myOverwritePolicyCombo = new ComboBox<>(OverwritePolicy.values());
        myOverwritePolicyCombo.setRenderer(OverwritePolicyRenderer.INSTANCE);
        myOverwritePolicyCombo.setSelectedItem(
            TurtleCommanderSettings.getInstance().getDefaultOverwritePolicy());

        myDefaultViewModeCombo = new ComboBox<>(new DefaultComboBoxModel<>(VIEW_MODE_LABELS));
        myDefaultViewModeCombo.setSelectedItem(viewModeLabelFor(settings.defaultViewMode));
        myPanelLayoutCombo = new ComboBox<>(new DefaultComboBoxModel<>(LAYOUT_LABELS));
        myPanelLayoutCombo.setSelectedItem(layoutLabelFor(settings.panelLayout));

        myThumbnailSizeCombo = new ComboBox<>(new DefaultComboBoxModel<>(ThumbnailSize.values()));
        myThumbnailSizeCombo.setSelectedItem(ThumbnailSize.fromName(settings.thumbnailSize));
        myThumbnailSizeCombo.setRenderer(labelRenderer(ThumbnailSize::getLabel));

        myFileSizeFormatCombo = new ComboBox<>(new DefaultComboBoxModel<>(FileSizeFormat.values()));
        myFileSizeFormatCombo.setSelectedItem(FileSizeFormat.fromName(settings.fileSizeFormat));
        myFileSizeFormatCombo.setRenderer(labelRenderer(FileSizeFormat::getLabel));

        buildDateTimeFields(settings);

        myAppearanceEditor = new AppearanceEditor(settings);
        myColumnsEditor = new ColumnsEditor(TurtleCommanderSettings.getInstance().getEffectiveColumns());
        myFavoritesEditor = new FavoritesEditor();
        myColorRulesEditor = new ColorRulesEditor(null);
        myColorRulesEditor.resetFrom(settings);

        return buildRoot();
    }

    private void buildDateTimeFields(TurtleCommanderSettings.State settings) {
        myDateTimePreviewLabel = new JBLabel("");
        myDateTimeFormatField = new JBTextField(settings.dateTimeFormat.isBlank()
            ? TurtleCommanderSettings.DEFAULT_DATE_TIME_FORMAT : settings.dateTimeFormat, 20);
        myDateTimeFormatField.setToolTipText(
            "Java DateTimeFormatter pattern, e.g. yyyy-MM-dd HH:mm, dd/MM/yyyy, MMM d yyyy h:mm a");
        myDateTimeFormatField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                updateDateTimePreview();
            }
        });
        updateDateTimePreview();

        myExportDateTimeFormatField = new JBTextField(settings.exportDateTimeFormat, 20);
        myExportDateTimeFormatField.setToolTipText("Date/time format for CSV/JSON export. Leave blank "
            + "for ISO-8601 UTC (e.g. 2026-06-16T12:34:56Z); otherwise a Java DateTimeFormatter "
            + "pattern in local time.");
    }

    private JComponent buildRoot() {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        for (JCheckBox checkBox : new JCheckBox[]{myHighlightingCheckBox, myCommandBarCheckBox}) {
            checkBox.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            inner.add(checkBox);
        }
        inner.add(buildCommandHistoryRow());
        for (JCheckBox checkBox : new JCheckBox[]{myHideDriveSelectorCheckBox, myHideStatusBarCheckBox,
            myDeleteToRecycleBinCheckBox, mySortWithDirectoriesCheckBox, myNaturalNameSortCheckBox,
            myCalculateDirectorySizeCheckBox, myOpenDirectoriesWithSingleClickCheckBox}) {
            checkBox.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            inner.add(checkBox);
        }

        inner.add(Box.createVerticalStrut(8));
        inner.add(labelledRow("Default copy/move policy:  ", myOverwritePolicyCombo));
        inner.add(labelledRow("Default tab view:  ", myDefaultViewModeCombo));
        inner.add(labelledRow("Layout:  ", myPanelLayoutCombo));
        inner.add(buildThumbnailRow());
        inner.add(labelledRow("File size format:  ", myFileSizeFormatCombo));
        inner.add(buildDateTimeRow());
        inner.add(buildExportDateTimeRow());

        inner.add(Box.createVerticalStrut(8));
        inner.add(myAppearanceEditor.getPanel());
        inner.add(Box.createVerticalStrut(8));
        inner.add(myColumnsEditor.getPanel());
        inner.add(Box.createVerticalStrut(8));
        inner.add(myColorRulesEditor.getPanel());
        inner.add(Box.createVerticalStrut(8));
        inner.add(myFavoritesEditor.getPanel());

        // NORTH keeps the stack at its natural height rather than stretching it down the dialog.
        JPanel root = new JPanel(new BorderLayout());
        root.add(inner, BorderLayout.NORTH);
        return root;
    }

    private static JPanel row() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return row;
    }

    private static JPanel labelledRow(String label, JComponent field) {
        JPanel row = row();
        row.add(new JBLabel(label));
        row.add(field);
        return row;
    }

    private JPanel buildCommandHistoryRow() {
        JButton clearButton = new JButton("Clear");
        clearButton.setToolTipText("Remove all saved command-line history");
        clearButton.addActionListener(e -> {
            // History is per project, so every open project's is cleared.
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                FileManagerStateService.getInstance(project).clearCommandHistory();
            }
        });

        JPanel row = row();
        row.add(mySaveCommandHistoryCheckBox);
        row.add(Box.createHorizontalStrut(8));
        row.add(clearButton);
        return row;
    }

    private JPanel buildThumbnailRow() {
        ThumbnailCache cache = ThumbnailCache.getInstance();
        JBLabel cacheSizeLabel = new JBLabel(FileUtils.formatSize(cache.getCacheSize()));
        JButton clearCacheButton = new JButton("Clear");
        clearCacheButton.addActionListener(e -> {
            cache.clearCache();
            cacheSizeLabel.setText(FileUtils.formatSize(cache.getCacheSize()));
        });

        JPanel row = row();
        row.add(new JBLabel("Thumbnail size:  "));
        row.add(myThumbnailSizeCombo);
        row.add(Box.createHorizontalStrut(16));
        row.add(new JBLabel("Cache: "));
        row.add(cacheSizeLabel);
        row.add(Box.createHorizontalStrut(8));
        row.add(clearCacheButton);
        return row;
    }

    private JPanel buildDateTimeRow() {
        JPanel row = row();
        row.add(new JBLabel("Date/time format:  "));
        row.add(myDateTimeFormatField);
        row.add(Box.createHorizontalStrut(8));
        row.add(myDateTimePreviewLabel);
        return row;
    }

    private JPanel buildExportDateTimeRow() {
        JPanel row = row();
        row.add(new JBLabel("Export date/time format:  "));
        row.add(myExportDateTimeFormatField);
        row.add(Box.createHorizontalStrut(8));
        row.add(new JBLabel("(blank = ISO-8601 UTC)"));
        return row;
    }

    /**
     * Renders a sample timestamp beside the pattern field, so the user sees what the format does —
     * and an inline error for a bad pattern — instead of finding out after Apply.
     */
    private void updateDateTimePreview() {
        if (myDateTimePreviewLabel == null || myDateTimeFormatField == null) {
            return;
        }
        String pattern = myDateTimeFormatField.getText().trim();
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
        myDateTimePreviewLabel.setText("\u2192 " + text);
    }

    // --- Configurable contract -------------------------------------------------------------------------

    @RequiredUIAccess
    @Override
    public boolean isModified() {
        TurtleCommanderSettings.State settings = TurtleCommanderSettings.getInstance().getState();
        return myHighlightingCheckBox.isSelected() != settings.enableFileNameHighlighting
            || myCommandBarCheckBox.isSelected() != settings.showCommandBar
            || mySaveCommandHistoryCheckBox.isSelected() != settings.saveCommandHistory
            || myHideDriveSelectorCheckBox.isSelected() != settings.hideDriveSelector
            || myHideStatusBarCheckBox.isSelected() != settings.hideStatusBar
            || myDeleteToRecycleBinCheckBox.isSelected() != settings.deleteToRecycleBin
            || mySortWithDirectoriesCheckBox.isSelected() != settings.sortWithDirectories
            || myNaturalNameSortCheckBox.isSelected() != settings.naturalNameSort
            || myCalculateDirectorySizeCheckBox.isSelected() != settings.calculateDirectorySize
            || myOpenDirectoriesWithSingleClickCheckBox.isSelected() != settings.openDirectoriesWithSingleClick
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

        settings.enableFileNameHighlighting = myHighlightingCheckBox.isSelected();
        settings.showCommandBar = myCommandBarCheckBox.isSelected();
        settings.saveCommandHistory = mySaveCommandHistoryCheckBox.isSelected();
        settings.hideDriveSelector = myHideDriveSelectorCheckBox.isSelected();
        settings.hideStatusBar = myHideStatusBarCheckBox.isSelected();
        settings.deleteToRecycleBin = myDeleteToRecycleBinCheckBox.isSelected();
        settings.sortWithDirectories = mySortWithDirectoriesCheckBox.isSelected();
        settings.naturalNameSort = myNaturalNameSortCheckBox.isSelected();
        settings.calculateDirectorySize = myCalculateDirectorySizeCheckBox.isSelected();
        settings.openDirectoriesWithSingleClick = myOpenDirectoriesWithSingleClickCheckBox.isSelected();

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

        myHighlightingCheckBox.setSelected(settings.enableFileNameHighlighting);
        myCommandBarCheckBox.setSelected(settings.showCommandBar);
        mySaveCommandHistoryCheckBox.setSelected(settings.saveCommandHistory);
        myHideDriveSelectorCheckBox.setSelected(settings.hideDriveSelector);
        myHideStatusBarCheckBox.setSelected(settings.hideStatusBar);
        myDeleteToRecycleBinCheckBox.setSelected(settings.deleteToRecycleBin);
        mySortWithDirectoriesCheckBox.setSelected(settings.sortWithDirectories);
        myNaturalNameSortCheckBox.setSelected(settings.naturalNameSort);
        myCalculateDirectorySizeCheckBox.setSelected(settings.calculateDirectorySize);
        myOpenDirectoriesWithSingleClickCheckBox.setSelected(settings.openDirectoriesWithSingleClick);

        myOverwritePolicyCombo.setSelectedItem(
            TurtleCommanderSettings.getInstance().getDefaultOverwritePolicy());
        myDefaultViewModeCombo.setSelectedItem(viewModeLabelFor(settings.defaultViewMode));
        myPanelLayoutCombo.setSelectedItem(layoutLabelFor(settings.panelLayout));
        myThumbnailSizeCombo.setSelectedItem(ThumbnailSize.fromName(settings.thumbnailSize));
        myFileSizeFormatCombo.setSelectedItem(FileSizeFormat.fromName(settings.fileSizeFormat));

        myDateTimeFormatField.setText(settings.dateTimeFormat.isBlank()
            ? TurtleCommanderSettings.DEFAULT_DATE_TIME_FORMAT : settings.dateTimeFormat);
        myExportDateTimeFormatField.setText(settings.exportDateTimeFormat);
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

    private OverwritePolicy selectedOverwritePolicy() {
        Object selected = myOverwritePolicyCombo.getSelectedItem();
        return selected instanceof OverwritePolicy policy ? policy : OverwritePolicy.ASK;
    }

    private String selectedViewMode() {
        return switch (String.valueOf(myDefaultViewModeCombo.getSelectedItem())) {
            case "List" -> "LIST";
            case "Thumbnail" -> "THUMBNAIL";
            case "Tree" -> "TREE";
            default -> "TABLE";
        };
    }

    private static String viewModeLabelFor(String name) {
        return switch (name) {
            case "LIST" -> "List";
            case "THUMBNAIL" -> "Thumbnail";
            case "TREE" -> "Tree";
            default -> "Table";
        };
    }

    private String selectedPanelLayout() {
        return switch (String.valueOf(myPanelLayoutCombo.getSelectedItem())) {
            case "Vertical Dual-panel" -> PanelLayout.VERTICAL.name();
            case "Single-panel" -> PanelLayout.SINGLE.name();
            default -> PanelLayout.HORIZONTAL.name();
        };
    }

    private static String layoutLabelFor(String name) {
        if (PanelLayout.VERTICAL.name().equals(name)) {
            return "Vertical Dual-panel";
        }
        return PanelLayout.SINGLE.name().equals(name) ? "Single-panel" : "Horizontal Dual-panel";
    }

    private String selectedThumbnailSize() {
        Object selected = myThumbnailSizeCombo.getSelectedItem();
        return (selected instanceof ThumbnailSize size ? size : ThumbnailSize.SMALL).name();
    }

    private String selectedFileSizeFormat() {
        Object selected = myFileSizeFormatCombo.getSelectedItem();
        return (selected instanceof FileSizeFormat format ? format : FileSizeFormat.AUTO_BINARY).name();
    }

    private String enteredDateTimeFormat() {
        String text = myDateTimeFormatField.getText().trim();
        return text.isEmpty() ? TurtleCommanderSettings.DEFAULT_DATE_TIME_FORMAT : text;
    }

    /** Blank is meaningful here — it selects the ISO-8601 UTC default — so it is kept verbatim. */
    private String enteredExportDateTimeFormat() {
        return myExportDateTimeFormatField.getText().trim();
    }

    private static <T> DefaultListCellRenderer labelRenderer(Function<T, String> label) {
        return new DefaultListCellRenderer() {
            @Override
            @SuppressWarnings("unchecked")
            public Component getListCellRendererComponent(JList<?> list, @Nullable Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component component =
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null) {
                    setText(label.apply((T) value));
                }
                return component;
            }
        };
    }
}
