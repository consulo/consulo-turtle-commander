package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.ui.ex.action.AnActionEvent;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * Jumps to a favourite by slot number. One concrete subclass per slot, since Consulo registers
 * actions by type and each slot needs its own id and shortcut.
 */
public abstract class QuickAccessFavoriteAction extends FileManagerAction.ToolWindowScoped {
    private final int mySlot;

    protected QuickAccessFavoriteAction(int slot) {
        super(LocalizeValue.localizeTODO("Favorite " + slot), "ctrl " + slot);
        mySlot = slot;
    }

    @Override
    public void update(AnActionEvent e) {
        String path = favoritePath(e);
        // The label names the target so the keymap and Find Action are useful, rather than showing
        // nine identically-named entries.
        if (path != null) {
            Path fileName = Path.of(path).getFileName();
            e.getPresentation().setText(
                LocalizeValue.localizeTODO("Go to " + (fileName != null ? fileName : path)));
        }
        else {
            e.getPresentation().setText(
                LocalizeValue.localizeTODO("Favorite " + mySlot + " (not set)"));
        }
        e.getPresentation().setEnabled(ActionTargets.isToolWindowActive(e) && path != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        String path = favoritePath(e);
        FileManagerStateService stateService = ActionTargets.stateService(e);
        FileManagerPanel panel = stateService != null ? stateService.getActivePanel() : null;
        if (path != null && panel != null) {
            panel.openDirectoryInNewTab(Path.of(path), null);
        }
    }

    private @Nullable String favoritePath(AnActionEvent e) {
        FileManagerStateService stateService = ActionTargets.stateService(e);
        if (stateService == null) {
            return null;
        }
        List<String> favorites = stateService.getFavorites();
        return mySlot <= favorites.size() ? favorites.get(mySlot - 1) : null;
    }
}
