package consulo.turtlecommander.ui;

import consulo.turtlecommander.util.DriveLabels;
import consulo.ui.ex.awt.ComboBox;
import org.jspecify.annotations.Nullable;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Owns the drive selector and the rules for when it may be mutated.
 *
 * <p>Root updates arrive asynchronously from the shared poller, but rebuilding the combo's item
 * list while its popup is open would yank the selection out from under the user — and while the
 * tab is inside an archive the combo isn't showing drives at all. Updates arriving in either state
 * are parked and applied once it's safe.
 */
public class DriveComboController {
    private final ComboBox<String> myCombo = new ComboBox<>();
    private final BooleanSupplier mySuppressUpdates;

    private boolean myUpdating;
    private boolean myPopupOpen;
    private @Nullable List<String> myPendingRoots;

    /** @param suppressUpdates true while the combo must not be touched (e.g. inside an archive) */
    public DriveComboController(BooleanSupplier suppressUpdates) {
        mySuppressUpdates = suppressUpdates;
        myCombo.setRenderer(new DriveComboRenderer());
    }

    public ComboBox<String> getComponent() {
        return myCombo;
    }

    /** True while this controller is rebuilding the item list, so selection events are ours. */
    public boolean isUpdating() {
        return myUpdating;
    }

    public void setPopupOpen(boolean open) {
        myPopupOpen = open;
    }

    public boolean isPopupOpen() {
        return myPopupOpen;
    }

    /** Applies the roots now, or parks them if the combo can't safely be touched. */
    public void acceptRoots(List<String> roots, Path currentPath) {
        if (myPopupOpen || mySuppressUpdates.getAsBoolean()) {
            myPendingRoots = roots;
            return;
        }
        applyRoots(roots, currentPath);
    }

    /**
     * Applies a parked update once the popup has closed. Deferred through invokeLater so the
     * popup-close handler finishes reading the user's selection before the item list is rebuilt
     * underneath it.
     */
    public void flushPendingLater(Path currentPath) {
        if (myPendingRoots == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            List<String> pending = myPendingRoots;
            if (pending == null || myPopupOpen || mySuppressUpdates.getAsBoolean()) {
                return;
            }
            myPendingRoots = null;
            applyRoots(pending, currentPath);
        });
    }

    public void clearPending() {
        myPendingRoots = null;
    }

    /**
     * Moves the selection to the deepest root containing {@code path}, without firing the
     * navigation that a user-driven selection would.
     */
    public void syncSelection(Path path) {
        int bestIndex = -1;
        int bestLength = -1;
        for (int i = 0; i < myCombo.getItemCount(); i++) {
            String item = myCombo.getItemAt(i);
            if (path.startsWith(item) && item.length() > bestLength) {
                bestIndex = i;
                bestLength = item.length();
            }
        }
        if (bestIndex < 0) {
            return;
        }
        myUpdating = true;
        try {
            myCombo.setSelectedIndex(bestIndex);
        }
        finally {
            myUpdating = false;
        }
    }

    private void applyRoots(List<String> roots, Path currentPath) {
        List<String> current = new ArrayList<>(myCombo.getItemCount());
        for (int i = 0; i < myCombo.getItemCount(); i++) {
            current.add(myCombo.getItemAt(i));
        }
        if (current.equals(roots)) {
            return;
        }

        myUpdating = true;
        try {
            myCombo.removeAllItems();
            for (String root : roots) {
                myCombo.addItem(root);
            }

            String bestMatch = deepestRootFor(currentPath, roots);
            if (bestMatch != null) {
                myCombo.setSelectedItem(bestMatch);
            }

            myCombo.setPrototypeDisplayValue(widestLabel(roots));
            myCombo.revalidate();
        }
        finally {
            myUpdating = false;
        }
    }

    /** The most specific root containing {@code currentPath}, falling back to the first root. */
    private static @Nullable String deepestRootFor(Path currentPath, List<String> roots) {
        String best = null;
        for (String root : roots) {
            if (currentPath.startsWith(root) && (best == null || root.length() > best.length())) {
                best = root;
            }
        }
        if (best != null) {
            return best;
        }
        return roots.isEmpty() ? null : roots.get(0);
    }

    /** Sizes the popup off the widest rendered label, not the widest raw path. */
    private static String widestLabel(List<String> roots) {
        String widest = "";
        int widestLength = -1;
        for (String root : roots) {
            int length = DriveLabels.getDisplayText(root).length();
            if (length > widestLength) {
                widestLength = length;
                widest = root;
            }
        }
        return widest;
    }
}
