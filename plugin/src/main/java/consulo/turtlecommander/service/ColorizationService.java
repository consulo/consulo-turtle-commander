package consulo.turtlecommander.service;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.settings.ColorRule;
import consulo.turtlecommander.settings.ColorRuleManager;
import consulo.turtlecommander.settings.ColorizationMode;
import consulo.turtlecommander.settings.ResolvedStyle;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.settings.TurtleCommanderSettingsListener;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Application service used by the renderers. Keeps a {@link ColorizationResolver}, a settings
 * snapshot refreshed whenever settings change, and exposes one {@link #resolve} entry point.
 */
@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class ColorizationService {
    private final ColorizationResolver myResolver = new ColorizationResolver();

    private record Snapshot(boolean enabled, ColorizationMode mode, List<ColorRule> activeRules,
                            boolean anyContainsMatcher) {
    }

    private volatile @Nullable Snapshot mySnapshot;
    private volatile boolean mySubscribed;

    public static ColorizationService getInstance() {
        return Application.get().getInstance(ColorizationService.class);
    }

    public ResolvedStyle resolve(FileEntry entry) {
        Snapshot snapshot = mySnapshot;
        if (snapshot == null) {
            snapshot = computeSnapshot();
            mySnapshot = snapshot;
            ensureSubscribed();
        }
        return myResolver.resolve(entry, snapshot.activeRules(), snapshot.mode(), snapshot.enabled(),
            snapshot.anyContainsMatcher());
    }

    public void refreshSnapshot() {
        mySnapshot = computeSnapshot();
    }

    private Snapshot computeSnapshot() {
        TurtleCommanderSettings.State state = TurtleCommanderSettings.getInstance().getState();
        List<ColorRule> active = ColorRuleManager.getActiveRulesForEvaluation(state);
        return new Snapshot(
            state.enableFileNameHighlighting,
            ColorRuleManager.getMode(state),
            active,
            ColorizationResolver.hasContainsMatcher(active)
        );
    }

    private synchronized void ensureSubscribed() {
        if (mySubscribed) {
            return;
        }
        mySubscribed = true;
        Application.get().getMessageBus().connect().subscribe(TurtleCommanderSettingsListener.class, () -> {
            refreshSnapshot();
            myResolver.clearCache();
        });
    }
}
