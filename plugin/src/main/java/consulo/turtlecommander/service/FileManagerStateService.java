package consulo.turtlecommander.service;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.project.Project;
import consulo.turtlecommander.settings.PanelLayout;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.ui.CommandInputBar;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.turtlecommander.ui.FileTab;
import consulo.ui.ex.awt.OnePixelSplitter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-project home for the tool window's layout: which panels exist, what they show, the favorites
 * list, and the command-line history. Persisted to {@code turtleCommander.xml}.
 *
 * <p>The panels register themselves here on creation, which is also how any action can find "the
 * other panel" without threading references through every call site.
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@State(name = "TurtleCommanderFileManager", storages = @Storage("turtleCommander.xml"))
public class FileManagerStateService implements PersistentStateComponent<FileManagerState> {
    private static final int MAX_COMMAND_HISTORY = 50;

    private final Project myProject;

    private FileManagerState myState = new FileManagerState();

    private @Nullable FileManagerPanel myLeftPanel;
    private @Nullable FileManagerPanel myRightPanel;
    private @Nullable OnePixelSplitter mySplitter;
    private @Nullable Runnable mySwapSinglePanelCallback;
    private @Nullable CommandInputBar myCommandInputBar;

    @Inject
    public FileManagerStateService(Project project) {
        myProject = project;
    }

    public static FileManagerStateService getInstance(Project project) {
        return project.getInstance(FileManagerStateService.class);
    }

    public void registerPanels(FileManagerPanel left, FileManagerPanel right,
                               @Nullable OnePixelSplitter splitter) {
        myLeftPanel = left;
        myRightPanel = right;
        mySplitter = splitter;
    }

    public void updateSplitter(@Nullable OnePixelSplitter splitter) {
        mySplitter = splitter;
    }

    public void setSwapSinglePanelCallback(@Nullable Runnable callback) {
        mySwapSinglePanelCallback = callback;
    }

    public void registerCommandInputBar(CommandInputBar bar) {
        myCommandInputBar = bar;
    }

    public @Nullable FileManagerPanel getLeftPanel() {
        return myLeftPanel;
    }

    public @Nullable FileManagerPanel getRightPanel() {
        return myRightPanel;
    }

    /** Shows the bottom command line, targeting whichever panel currently holds focus. */
    public void activateCommandInput() {
        if (myCommandInputBar != null) {
            myCommandInputBar.activate(getActivePanel());
        }
    }

    public @Nullable FileManagerPanel getActivePanel() {
        if (myLeftPanel == null || myRightPanel == null) {
            return null;
        }
        return myLeftPanel.hasFocusInPanel() ? myLeftPanel : myRightPanel;
    }

    public @Nullable FileTab getActiveTab() {
        FileManagerPanel panel = getActivePanel();
        return panel != null ? panel.getActiveTab() : null;
    }

    /**
     * Moves focus to the opposite panel. While the command line has focus it retargets that instead,
     * so Tab cycles the command's target rather than yanking focus out of the field.
     */
    public void switchToOtherPanel() {
        if (myCommandInputBar != null && myCommandInputBar.hasFieldFocus()) {
            myCommandInputBar.switchTargetPanel();
            return;
        }
        if (myLeftPanel == null || myRightPanel == null) {
            return;
        }
        boolean currentlyLeft = myLeftPanel.hasFocusInPanel();
        // In single-panel layout there is nowhere to move focus to — swap which panel is shown.
        if (PanelLayout.SINGLE.name().equals(TurtleCommanderSettings.getInstance().getState().panelLayout)
            && mySwapSinglePanelCallback != null) {
            mySwapSinglePanelCallback.run();
        }
        if (currentlyLeft) {
            myRightPanel.focusActiveTab();
        }
        else {
            myLeftPanel.focusActiveTab();
        }
    }

    // --- Command history ----------------------------------------------------------------------------

    /** Oldest first. */
    public List<String> getCommandHistory() {
        return List.copyOf(myState.commandHistory);
    }

    /**
     * Records {@code command} as the most recent entry, de-duplicating and capping the size. A no-op
     * when the user has turned command-history persistence off.
     */
    public void addCommandToHistory(String command) {
        if (!TurtleCommanderSettings.getInstance().getState().saveCommandHistory) {
            return;
        }
        String trimmed = command.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        myState.commandHistory.remove(trimmed);
        myState.commandHistory.add(trimmed);
        while (myState.commandHistory.size() > MAX_COMMAND_HISTORY) {
            myState.commandHistory.remove(0);
        }
    }

    public void clearCommandHistory() {
        myState.commandHistory.clear();
    }

    // --- Favorites ----------------------------------------------------------------------------------

    public void addFavorite(String path, String color) {
        for (FavoriteEntry entry : myState.favoriteEntries) {
            if (entry.path.equals(path)) {
                return;
            }
        }
        myState.favoriteEntries.add(new FavoriteEntry(path, color, ""));
        fireFavoritesChanged();
    }

    public void removeFavorite(String path) {
        if (myState.favoriteEntries.removeIf(entry -> entry.path.equals(path))) {
            fireFavoritesChanged();
        }
    }

    public List<String> getFavorites() {
        List<String> paths = new ArrayList<>(myState.favoriteEntries.size());
        for (FavoriteEntry entry : myState.favoriteEntries) {
            paths.add(entry.path);
        }
        return paths;
    }

    public List<FavoriteEntry> getFavoriteEntries() {
        return List.copyOf(myState.favoriteEntries);
    }

    public void setFavoriteEntries(List<FavoriteEntry> entries) {
        myState.favoriteEntries.clear();
        myState.favoriteEntries.addAll(entries);
        fireFavoritesChanged();
    }

    private void fireFavoritesChanged() {
        myProject.getMessageBus().syncPublisher(FavoritesChangeListener.class).favoritesChanged();
    }

    // --- Persistence ---------------------------------------------------------------------------------

    @Override
    public FileManagerState getState() {
        if (myLeftPanel != null) {
            myState.leftPanel = myLeftPanel.saveState();
        }
        if (myRightPanel != null) {
            myState.rightPanel = myRightPanel.saveState();
        }
        if (mySplitter != null) {
            myState.splitProportion = mySplitter.getProportion();
        }
        return myState;
    }

    @Override
    public void loadState(FileManagerState state) {
        myState = state;
    }
}
