package consulo.turtlecommander.ui;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.turtlecommander.service.FileManagerState;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.SplitLayoutPosition;
import consulo.ui.layout.TwoComponentSplitLayout;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * The file manager in the unified UI: two panes side by side. It edits the panel states the state service persists, so
 * the tabs and their locations survive a restart.
 */
public final class UnifiedFileManagerView implements Disposable {
    private final TwoComponentSplitLayout mySplit = TwoComponentSplitLayout.create(SplitLayoutPosition.HORIZONTAL);
    private final FileManagerStateService myStateService;
    private @Nullable UnifiedFilePane myLeft;
    /** Each pane asks for the other lazily, as the two are built one after the other. */
    private @Nullable UnifiedFilePane myRight;
    /** The pane the actions act on - the one last used. */
    private UnifiedFilePane myActivePane;

    @RequiredUIAccess
    public UnifiedFileManagerView(Project project) {
        myStateService = FileManagerStateService.getInstance(project);
        FileManagerState state = myStateService.getState();
        Path fallbackPath = initialPath(project);

        myLeft = new UnifiedFilePane(project, state.leftPanel, fallbackPath, () -> myRight, pane -> myActivePane = pane);
        myRight = new UnifiedFilePane(project, state.rightPanel, fallbackPath, () -> myLeft, pane -> myActivePane = pane);
        myActivePane = myLeft;
        Disposer.register(this, myLeft);
        Disposer.register(this, myRight);

        mySplit.setFirstComponent(myLeft.getComponent());
        mySplit.setSecondComponent(myRight.getComponent());
        mySplit.setProportion(Math.round(state.splitProportion * 100));

        myStateService.setUnifiedActiveTab(this::getActiveTab);
        myStateService.setUnifiedActivePane(() -> myActivePane);
    }

    public @Nullable UnifiedFileTab getActiveTab() {
        return myActivePane.getActiveTab();
    }

    public Component getComponent() {
        return mySplit;
    }

    private static Path initialPath(Project project) {
        String basePath = project.getBasePath();
        return basePath != null ? Path.of(basePath) : Platform.current().user().homePath();
    }

    @Override
    public void dispose() {
        myStateService.setUnifiedActiveTab(null);
        myStateService.setUnifiedActivePane(null);
    }
}
