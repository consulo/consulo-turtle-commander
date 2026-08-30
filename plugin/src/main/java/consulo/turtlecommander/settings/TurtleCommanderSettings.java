package consulo.turtlecommander.settings;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.turtlecommander.service.OverwritePolicy;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.awt.Font;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@State(name = "TurtleCommanderSettings", storages = @Storage("turtleCommanderSettings.xml"))
public class TurtleCommanderSettings implements PersistentStateComponent<TurtleCommanderSettings.State> {
    /** Default pattern for date/time columns. */
    public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm";

    public static final String DEFAULT_EXPORT_DATE_TIME_FORMAT = "";

    private static final int MAX_SEARCH_HISTORY = 10;
    private static final int DEFAULT_PANEL_FONT_SIZE = 13;
    private static final int DEFAULT_TAB_FONT_SIZE = 12;

    public static class State {
        public boolean enableFileNameHighlighting = true;
        public boolean showCommandBar = true;
        /** When true, commands run from the command line are persisted across sessions. */
        public boolean saveCommandHistory = true;
        public boolean hideDriveSelector = false;
        public boolean hideStatusBar = false;

        /** One of {@link OverwritePolicy}; ASK keeps the per-file prompt. */
        public String defaultOverwritePolicy = OverwritePolicy.ASK.name();

        public boolean deleteToRecycleBin = false;
        public String panelFontFamily = "";
        public int panelFontSize = 0;
        public String tabFontFamily = "";
        public int tabFontSize = 0;
        public String defaultViewMode = "TABLE";
        public boolean sortWithDirectories = false;
        public boolean naturalNameSort = true;
        public boolean calculateDirectorySize = true;
        /** When true, a single left-click on a directory navigates into it. */
        public boolean openDirectoriesWithSingleClick = false;
        public String panelLayout = PanelLayout.HORIZONTAL.name();
        public String thumbnailSize = ThumbnailSize.SMALL.name();
        public String fileSizeFormat = FileSizeFormat.DEFAULT_NAME;
        public String dateTimeFormat = DEFAULT_DATE_TIME_FORMAT;
        public String exportDateTimeFormat = DEFAULT_EXPORT_DATE_TIME_FORMAT;

        public StyleSet styles = new StyleSet();
        public String themeName = "";
        public List<SavedTheme> themes = new ArrayList<>();
        public boolean themesInitialized = false;

        /** Styles saved before a theme was applied, so "Default" can restore them. */
        public StyleSet preThemeStyles = new StyleSet();

        public List<ColumnConfig> columns = new ArrayList<>();

        public List<String> nameSearchHistory = new ArrayList<>();
        public List<String> regexpSearchHistory = new ArrayList<>();

        public List<SavedColorRule> colorRules = new ArrayList<>();
        public boolean colorRulesInitialized = false;
        /** Stored as a string so the XML serializer stays happy. */
        public String colorizationMode = ColorizationMode.WINNER.name();
    }

    private State myState = new State();

    public static TurtleCommanderSettings getInstance() {
        return Application.get().getInstance(TurtleCommanderSettings.class);
    }

    @Override
    public State getState() {
        ThemeManager.ensureInitialThemes(myState);
        ColorRuleManager.ensureInitialRules(myState);
        return myState;
    }

    @Override
    public void loadState(State state) {
        myState = state;
    }

    public List<String> getNameSearchHistory(boolean regexp) {
        return regexp ? myState.regexpSearchHistory : myState.nameSearchHistory;
    }

    public void addNameSearchHistory(String pattern, boolean regexp) {
        if (pattern.isBlank()) {
            return;
        }
        List<String> history = getNameSearchHistory(regexp);
        history.remove(pattern);
        history.add(0, pattern);
        while (history.size() > MAX_SEARCH_HISTORY) {
            history.remove(history.size() - 1);
        }
    }

    /** Saved columns, with any column missing from the saved set appended at its default visibility. */
    public List<ColumnConfig> getEffectiveColumns() {
        List<String> available = ColumnConfig.availableColumnIds();

        List<ColumnConfig> saved = new ArrayList<>();
        Set<String> savedIds = new HashSet<>();
        for (ColumnConfig config : myState.columns) {
            if (available.contains(config.id)) {
                saved.add(config);
                savedIds.add(config.id);
            }
        }
        if (saved.isEmpty()) {
            return ColumnConfig.defaults();
        }

        for (String id : available) {
            if (!savedIds.contains(id)) {
                saved.add(ColumnConfig.of(id, ColumnConfig.defaultVisible(id)));
            }
        }
        return saved;
    }

    public @Nullable Font getPanelFont() {
        // The new panelStyle wins when it carries anything; otherwise fall back to the legacy fields.
        if (!myState.styles.panelStyle.isDefault()) {
            return myState.styles.panelStyle.getFont(null);
        }
        if (myState.panelFontFamily.isEmpty()) {
            return null;
        }
        return new Font(myState.panelFontFamily, Font.PLAIN,
            myState.panelFontSize > 0 ? myState.panelFontSize : DEFAULT_PANEL_FONT_SIZE);
    }

    public int getPanelFontSize() {
        int styled = myState.styles.panelStyle.fontSize;
        return styled > 0 ? styled : myState.panelFontSize;
    }

    public @Nullable Font getTabFont() {
        if (!myState.styles.tabStyle.isDefault()) {
            return myState.styles.tabStyle.getFont(null);
        }
        if (myState.tabFontFamily.isEmpty()) {
            return null;
        }
        return new Font(myState.tabFontFamily, Font.PLAIN,
            myState.tabFontSize > 0 ? myState.tabFontSize : DEFAULT_TAB_FONT_SIZE);
    }

    public int getTabFontSize() {
        int styled = myState.styles.tabStyle.fontSize;
        return styled > 0 ? styled : myState.tabFontSize;
    }

    /** Parses {@link State#defaultOverwritePolicy}, falling back to ASK on garbage. */
    public OverwritePolicy getDefaultOverwritePolicy() {
        try {
            return OverwritePolicy.valueOf(myState.defaultOverwritePolicy);
        }
        catch (IllegalArgumentException e) {
            return OverwritePolicy.ASK;
        }
    }

    public FileSizeFormat getFileSizeFormat() {
        return FileSizeFormat.fromName(myState.fileSizeFormat);
    }

    /**
     * The user-configured date/time pattern, or {@link #DEFAULT_DATE_TIME_FORMAT} when blank. The
     * pattern is not validated here — callers must tolerate {@code DateTimeFormatter.ofPattern}
     * rejecting it.
     */
    public String getDateTimeFormat() {
        return myState.dateTimeFormat.isBlank() ? DEFAULT_DATE_TIME_FORMAT : myState.dateTimeFormat;
    }

    public String getExportDateTimeFormat() {
        return myState.exportDateTimeFormat;
    }

    public void fireSettingsChanged() {
        Application.get().getMessageBus().syncPublisher(TurtleCommanderSettingsListener.class).settingsChanged();
    }

}
