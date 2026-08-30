package consulo.turtlecommander.ui;

import consulo.turtlecommander.action.FileContextMenuState;
import consulo.turtlecommander.model.FileEntry;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionPopupMenu;
import consulo.ui.ex.action.AnAction;
import consulo.ui.Component;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

/**
 * Per-view right-click wiring.
 *
 * <p>Each view resolves the entry under the cursor — selecting it first when it isn't already part
 * of the selection — then hands off to the shared popup, which stashes the target in
 * {@link FileContextMenuState} and shows the registered action group.
 */
public final class FileTabContextMenuUtil {
    private static final String FILE_CONTEXT_MENU_GROUP = "TurtleCommander.FileContextMenu";

    private FileTabContextMenuUtil() {
    }

    public static void handleTableContextMenu(FileTab tab, MouseEvent e) {
        showContextMenu(tab, tab.getTable(), e, () -> {
            int row = tab.getTable().rowAtPoint(e.getPoint());
            if (row < 0) {
                return null;
            }
            if (!tab.getTable().isRowSelected(row)) {
                tab.getTable().setRowSelectionInterval(row, row);
            }
            return tab.getTableModel().getEntryAt(tab.getTable().convertRowIndexToModel(row));
        });
    }

    public static void handleListContextMenu(FileTab tab, MouseEvent e) {
        showContextMenu(tab, tab.getList(), e, () -> {
            int index = tab.getList().locationToIndex(e.getPoint());
            if (index < 0) {
                return null;
            }
            if (!tab.getList().isSelectedIndex(index)) {
                tab.getList().setSelectedIndex(index);
            }
            return tab.getListModel().getElementAt(index);
        });
    }

    public static void handleThumbnailContextMenu(FileTab tab, MouseEvent e) {
        showContextMenu(tab, tab.getThumbnailList(), e, () -> {
            int index = tab.getThumbnailList().locationToIndex(e.getPoint());
            if (index < 0) {
                return null;
            }
            if (!tab.getThumbnailList().isSelectedIndex(index)) {
                tab.getThumbnailList().setSelectedIndex(index);
            }
            return tab.getThumbnailListModel().getElementAt(index);
        });
    }

    public static void handleTreeContextMenu(FileTab tab, MouseEvent e) {
        showContextMenu(tab, tab.getTree(), e, () -> {
            TreePath treePath = tab.getTree().getPathForLocation(e.getX(), e.getY());
            if (treePath != null && !tab.getTree().isPathSelected(treePath)) {
                tab.getTree().setSelectionPath(treePath);
            }
            Object component = tab.getTree().getLastSelectedPathComponent();
            if (component instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof FileEntry entry) {
                return entry;
            }
            return null;
        });
    }

    /** Shown from the keyboard (Shift+F10), where there is no click to resolve an entry from. */
    public static void showContextMenuForSelection(FileTab tab) {
        FileContextMenuState.setClickedEntry(FileTabSelectionUtil.getSelectedEntry(tab));
        FileContextMenuState.setClickedTab(tab);
        showPopup(tab.activeViewComponent(), 0, 0);
    }

    private static void showContextMenu(FileTab tab, JComponent component, MouseEvent e,
                                        Supplier<@Nullable FileEntry> resolveEntry) {
        if (!e.isPopupTrigger()) {
            return;
        }

        FileContextMenuState.setClickedEntry(resolveEntry.get());
        FileContextMenuState.setClickedTab(tab);
        showPopup(component, e.getX(), e.getY());
    }

    private static void showPopup(JComponent component, int x, int y) {
        ActionManager actionManager = ActionManager.getInstance();
        AnAction action = actionManager.getAction(FILE_CONTEXT_MENU_GROUP);
        if (!(action instanceof ActionGroup group)) {
            FileContextMenuState.clear();
            return;
        }

        Component target = TargetAWT.wrap(component);
        ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(FILE_CONTEXT_MENU_GROUP, group);
        popupMenu.setTargetComponent(target);
        popupMenu.show(target, x, y);
    }
}
