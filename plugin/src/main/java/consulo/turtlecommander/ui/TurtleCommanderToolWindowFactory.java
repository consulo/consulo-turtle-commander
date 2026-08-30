package consulo.turtlecommander.ui;

import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.project.Project;
import consulo.turtlecommander.service.FileManagerState;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.service.FileOperationService;
import consulo.turtlecommander.service.ThumbnailCache;
import consulo.turtlecommander.settings.ColumnConfig;
import consulo.turtlecommander.settings.PanelLayout;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.settings.TurtleCommanderSettingsListener;
import consulo.ui.ex.awt.OnePixelSplitter;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the tool window: two panels, the layout hosting them, and the bottom bars.
 */
public class TurtleCommanderToolWindowFactory {
    public static final String ID = "Turtle Commander";

    /**
     * Columns only populated while visible, so turning one on requires a re-listing.
     */
    private static final Set<String> DATA_COLUMNS =
        Set.of("User", "Group", "Permissions", "Inode", "Links");


    public static JComponent createContent(Project project, VirtualFile file, Disposable disposable) {
        Path initialPath = resolveInitialPath(project);
        FileManagerStateService stateService = FileManagerStateService.getInstance(project);
        FileManagerState savedState = stateService.getState();

        FileManagerPanel leftPanel = new FileManagerPanel(project, initialPath, () -> initialPath);
        FileManagerPanel rightPanel = new FileManagerPanel(project, initialPath, () -> initialPath);
        leftPanel.setOtherPanel(rightPanel);
        rightPanel.setOtherPanel(leftPanel);

        leftPanel.setParentDisposable(disposable);
        rightPanel.setParentDisposable(disposable);

        leftPanel.restoreState(savedState.leftPanel, stateService);
        rightPanel.restoreState(savedState.rightPanel, stateService);

        LayoutHost layoutHost = new LayoutHost(leftPanel, rightPanel, stateService, savedState);
        layoutHost.apply();
        stateService.registerPanels(leftPanel, rightPanel, layoutHost.currentSplitter());
        stateService.setSwapSinglePanelCallback(layoutHost::swapSinglePanel);

        JPanel contentPanel = new JPanel(new BorderLayout());

        TurtleCommanderSettings settings = TurtleCommanderSettings.getInstance();
        CommandBar commandBar = new CommandBar(leftPanel, rightPanel, project, file, contentPanel, disposable);
        commandBar.setVisible(settings.getState().showCommandBar);

        CommandInputBar commandInputBar = new CommandInputBar(project, leftPanel, rightPanel);
        stateService.registerCommandInputBar(commandInputBar);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(commandInputBar, BorderLayout.NORTH);
        southPanel.add(commandBar, BorderLayout.SOUTH);

        contentPanel.add(layoutHost, BorderLayout.CENTER);
        contentPanel.add(southPanel, BorderLayout.SOUTH);

        subscribeToSettings(project, disposable, layoutHost, commandBar, contentPanel,
            leftPanel, rightPanel);

        return contentPanel;
    }

    /**
     * Hosts whichever arrangement the user has chosen. A layout change rebuilds the splitter, so
     * the current drag position is saved first and handed to the replacement.
     */
    private static final class LayoutHost extends JPanel {
        private final FileManagerPanel myLeftPanel;
        private final FileManagerPanel myRightPanel;
        private final FileManagerStateService myStateService;
        private final FileManagerState mySavedState;

        private @Nullable OnePixelSplitter mySplitter;
        private boolean mySingleShowsLeft = true;

        LayoutHost(FileManagerPanel leftPanel, FileManagerPanel rightPanel,
                   FileManagerStateService stateService, FileManagerState savedState) {
            super(new BorderLayout());
            myLeftPanel = leftPanel;
            myRightPanel = rightPanel;
            myStateService = stateService;
            mySavedState = savedState;
        }

        @Nullable
        OnePixelSplitter currentSplitter() {
            return mySplitter;
        }

        void apply() {
            if (mySplitter != null) {
                mySavedState.splitProportion = mySplitter.getProportion();
            }
            removeAll();
            mySplitter = null;

            PanelLayout layout = currentLayout();
            if (layout == PanelLayout.SINGLE) {
                add(mySingleShowsLeft ? myLeftPanel : myRightPanel, BorderLayout.CENTER);
            }
            else {
                OnePixelSplitter splitter =
                    new OnePixelSplitter(layout == PanelLayout.VERTICAL, mySavedState.splitProportion);
                splitter.setFirstComponent(myLeftPanel);
                splitter.setSecondComponent(myRightPanel);
                mySplitter = splitter;
                add(splitter, BorderLayout.CENTER);
            }

            myStateService.updateSplitter(mySplitter);
            revalidate();
            repaint();
        }

        /**
         * In single-panel mode there is nowhere to move focus, so "other panel" swaps the view.
         */
        void swapSinglePanel() {
            mySingleShowsLeft = !mySingleShowsLeft;
            if (currentLayout() == PanelLayout.SINGLE) {
                apply();
            }
        }

        private static PanelLayout currentLayout() {
            return PanelLayout.fromName(TurtleCommanderSettings.getInstance().getState().panelLayout);
        }
    }

    private static void subscribeToSettings(Project project, Disposable disposable, LayoutHost layoutHost,
                                            CommandBar commandBar, JPanel contentPanel,
                                            FileManagerPanel leftPanel, FileManagerPanel rightPanel) {
        Set<String>[] lastVisibleDataColumns = new Set[]{visibleDataColumns()};

        Application.get().getMessageBus().connect(disposable)
            .subscribe(TurtleCommanderSettingsListener.class, () -> {
                TurtleCommanderSettings.State state = TurtleCommanderSettings.getInstance().getState();

                // Column visibility decides which per-file fields (owner, group, permissions) get
                // populated, so a listing cached before the change may have them empty.
                project.getInstance(FileOperationService.class).clearListingCache();
                Set<String> nowVisible = visibleDataColumns();
                boolean gainedDataColumn = !lastVisibleDataColumns[0].containsAll(nowVisible);
                lastVisibleDataColumns[0] = nowVisible;
                if (gainedDataColumn) {
                    leftPanel.refreshActiveTab(false);
                    rightPanel.refreshActiveTab(false);
                }

                commandBar.setVisible(state.showCommandBar);
                commandBar.applyStyle(state.styles.commandBarStyle, state.styles.commandButtonStyle);

                for (FileManagerPanel panel : List.of(leftPanel, rightPanel)) {
                    panel.applyFonts();
                    panel.applyVisibilitySettings();
                }
                // Drop the in-memory icon cache so visible thumbnails re-render at the new size;
                // the per-size on-disk caches are kept.
                ThumbnailCache.getInstance().clearMemoryCache();
                for (FileManagerPanel panel : List.of(leftPanel, rightPanel)) {
                    panel.applyThumbnailSettings();
                    panel.reSort();
                }

                layoutHost.apply();
                contentPanel.revalidate();
                contentPanel.repaint();
            });
    }

    private static Set<String> visibleDataColumns() {
        Set<String> visible = new LinkedHashSet<>();
        for (ColumnConfig column : TurtleCommanderSettings.getInstance().getEffectiveColumns()) {
            if (column.visible && DATA_COLUMNS.contains(column.id)) {
                visible.add(column.id);
            }
        }
        return visible;
    }

    private static Path resolveInitialPath(Project project) {
        String basePath = project.getBasePath();
        return basePath != null ? Path.of(basePath) : Path.of(System.getProperty("user.home"));
    }
}
