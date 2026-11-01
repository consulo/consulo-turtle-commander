package consulo.turtlecommander.ui;

import consulo.turtlecommander.model.FileRoot;
import consulo.turtlecommander.util.UiTask;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Where picking a root from the path bar's root list navigates. */
public final class DriveSelectionUtil {
    private DriveSelectionUtil() {
    }

    public static void navigateToRoot(FileTab tab, FileRoot selected) {
        // Read the shared poller's cache rather than rescanning here: that would run on the UI
        // thread and can block on an unresponsive network drive.
        List<FileRoot> cachedRoots = tab.getFileOps().currentRoots();
        List<Path> availableRoots = new ArrayList<>();
        if (cachedRoots != null) {
            for (FileRoot root : cachedRoots) {
                availableRoots.add(root.path());
            }
        }

        Path target = resolveDriveSelectionTarget(selected.path(), tab.getOtherPanelPath(), availableRoots);
        if (tab.isInsideArchive()) {
            FileTabVfsUtil.closeVfsStackAsync(tab);
        }
        if (!target.equals(tab.getCurrentPath())) {
            UiTask.background(() -> FileTabNavigationUtil.navigateTo(tab, target));
        }
        tab.focusActiveView();
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
            if (deepestRoot == null || root.getNameCount() > deepestRoot.getNameCount()) {
                deepestRoot = root;
            }
        }

        return deepestRoot == null || deepestRoot.equals(selectedDrive) ? otherPanelPath : selectedDrive;
    }
}
