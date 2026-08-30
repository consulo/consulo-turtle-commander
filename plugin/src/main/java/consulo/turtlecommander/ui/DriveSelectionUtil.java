package consulo.turtlecommander.ui;

import consulo.turtlecommander.util.UiTask;
import consulo.ui.ex.awt.ComboBox;
import org.jspecify.annotations.Nullable;

import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Where the drive selector should navigate when the user picks a root, and the wiring for it. */
public final class DriveSelectionUtil {
    private DriveSelectionUtil() {
    }

    /**
     * Makes the tab's drive combo navigate on selection.
     *
     * <p>The work happens on popup close rather than on the selection event: the combo fires a
     * selection change while the user is still arrowing through the list, which would otherwise
     * navigate on every keystroke.
     */
    public static void install(FileTab tab) {
        DriveComboController controller = tab.getDriveCombo();
        ComboBox<String> combo = controller.getComponent();

        combo.addPopupMenuListener(new PopupMenuListener() {
            private @Nullable String mySelectionOnOpen;

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                controller.setPopupOpen(true);
                mySelectionOnOpen = selectedRoot(combo);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                controller.setPopupOpen(false);
                controller.flushPendingLater(tab.getCurrentPath());
                if (controller.isUpdating()) {
                    return;
                }
                String selected = selectedRoot(combo);
                // A dismissed dropdown keeps its selection; only a real change should navigate.
                if (selected != null && !selected.equals(mySelectionOnOpen)) {
                    navigateToDrive(tab, selected);
                }
                tab.focusActiveView();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                controller.setPopupOpen(false);
                controller.flushPendingLater(tab.getCurrentPath());
                tab.focusActiveView();
            }
        });
    }

    private static void navigateToDrive(FileTab tab, String selected) {
        // Read the shared poller's cache rather than calling File.listRoots() here: that would run
        // on the UI thread and can block on an unresponsive network drive.
        List<String> cachedRoots = tab.getFileOps().currentRoots();
        List<Path> availableRoots = new ArrayList<>();
        if (cachedRoots != null) {
            for (String root : cachedRoots) {
                availableRoots.add(Path.of(root));
            }
        }

        Path target = resolveDriveSelectionTarget(Path.of(selected), tab.getOtherPanelPath(), availableRoots);
        if (tab.isInsideArchive()) {
            FileTabVfsUtil.closeVfsStackAsync(tab);
        }
        if (!target.equals(tab.getCurrentPath())) {
            UiTask.background(() -> FileTabNavigationUtil.navigateTo(tab, target));
        }
    }

    private static @Nullable String selectedRoot(ComboBox<String> combo) {
        return combo.getSelectedItem() instanceof String root ? root : null;
    }

    /**
     * Picking a drive that already contains the other panel's location jumps to that location
     * instead of the bare root — but only when the selected drive is the <em>deepest</em> root
     * covering it. Otherwise (say, selecting "/" while the other panel sits under "/home/user")
     * the user is asking for the broader location, so that selection is honoured rather than
     * bouncing to the other panel.
     */
    public static Path resolveDriveSelectionTarget(Path selectedDrive, @Nullable Path otherPanelPath,
                                                   List<Path> availableRoots) {
        if (otherPanelPath == null
            || !otherPanelPath.startsWith(selectedDrive)
            || otherPanelPath.equals(selectedDrive)) {
            return selectedDrive;
        }

        Path deepestRoot = null;
        for (Path root : availableRoots) {
            if (!otherPanelPath.startsWith(root)) {
                continue;
            }
            if (deepestRoot == null || root.toString().length() > deepestRoot.toString().length()) {
                deepestRoot = root;
            }
        }

        return deepestRoot == null || deepestRoot.equals(selectedDrive) ? otherPanelPath : selectedDrive;
    }
}
