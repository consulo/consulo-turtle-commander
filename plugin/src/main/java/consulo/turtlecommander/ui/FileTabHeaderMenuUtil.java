package consulo.turtlecommander.ui;

import consulo.localize.LocalizeValue;
import consulo.turtlecommander.settings.ColumnConfig;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.ui.MenuItem;
import consulo.ui.PopupMenu;
import consulo.ui.ex.awt.util.IdeGlassPaneUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import org.jspecify.annotations.Nullable;

import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The table header's right-click column picker and its resize cursor. */
public final class FileTabHeaderMenuUtil {
    /** How close to a column boundary counts as "on the resize handle". */
    private static final int RESIZE_ZONE_PX = 3;

    private FileTabHeaderMenuUtil() {
    }

    public static void install(FileTab tab) {
        JTableHeader header = tab.getTable().getTableHeader();
        if (header == null) {
            return;
        }
        installColumnMenu(tab, header);
        installResizeCursor(header);
    }

    private static void installColumnMenu(FileTab tab, JTableHeader header) {
        MouseAdapter popupHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(tab, e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(tab, e);
            }
        };
        header.addMouseListener(popupHandler);
    }

    private static void showPopup(FileTab tab, MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TurtleCommanderSettings settings = TurtleCommanderSettings.getInstance();
        List<ColumnConfig> columns = settings.getEffectiveColumns();

        Map<String, ColumnConfig> byId = new LinkedHashMap<>();
        int visibleCount = 0;
        for (ColumnConfig column : columns) {
            byId.put(column.id, column);
            if (column.visible) {
                visibleCount++;
            }
        }

        PopupMenu popup = PopupMenu.create(TargetAWT.wrap(e.getComponent()));
        for (String id : menuOrder(tab.getTable(), columns)) {
            ColumnConfig column = byId.get(id);
            if (column == null) {
                continue;
            }
            MenuItem item = MenuItem.create(LocalizeValue.of(column.id));
            item.setChecked(column.visible);
            // The table needs at least one column, so the last visible one can't be turned off.
            if (column.visible && visibleCount <= 1) {
                item.setEnabled(false);
            }
            item.addClickListener(event -> {
                column.visible = !column.visible;
                persist(settings, columns);
                FileTabColumnUtil.reapplyColumnConfig(tab);
                settings.fireSettingsChanged();
            });
            popup.add(item);
        }
        popup.show(e.getX(), e.getY());
    }

    /** Visible columns in the order the table shows them, then the hidden ones in settings order. */
    private static List<String> menuOrder(JTable table, List<ColumnConfig> columns) {
        List<String> ordered = new ArrayList<>();
        TableColumnModel cm = table.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            String name = FileTabColumnUtil.columnNameOf(cm.getColumn(i).getModelIndex());
            if (name != null) {
                ordered.add(name);
            }
        }
        for (ColumnConfig column : columns) {
            if (!column.visible) {
                ordered.add(column.id);
            }
        }
        return ordered;
    }

    /** Settings hold their own copies, so the edited list is written back wholesale. */
    private static void persist(TurtleCommanderSettings settings, List<ColumnConfig> columns) {
        settings.getState().columns.clear();
        for (ColumnConfig column : columns) {
            ColumnConfig copy = new ColumnConfig();
            copy.id = column.id;
            copy.visible = column.visible;
            copy.style.copyFrom(column.style);
            settings.getState().columns.add(copy);
        }
    }

    /**
     * Shows a resize cursor over the column boundaries.
     *
     * <p>The IDE's glass pane sits above every tool window and owns cursor rendering, so
     * {@code setCursor} on the header has no visible effect. Requests go through the glass pane
     * instead, keyed by an object so different requestors don't clobber each other; a null cursor
     * withdraws the request.
     */
    private static void installResizeCursor(JTableHeader header) {
        Cursor hoverCursor = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
        Cursor dragCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
        Object cursorKey = new Object();

        header.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                requestCursor(header, cursorKey, nearColumnBoundary(header, e.getX()) ? hoverCursor : null);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (header.getResizingColumn() != null) {
                    requestCursor(header, cursorKey, dragCursor);
                }
            }
        });
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                requestCursor(header, cursorKey, nearColumnBoundary(header, e.getX()) ? hoverCursor : null);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (header.getResizingColumn() == null) {
                    requestCursor(header, cursorKey, null);
                }
            }
        });
    }

    private static void requestCursor(JTableHeader header, Object cursorKey, @Nullable Cursor cursor) {
        try {
            IdeGlassPaneUtil.find(header).setCursor(cursor, cursorKey);
        }
        catch (Exception e) {
            // No glass pane in this hierarchy (tests, detached header) — the cursor is cosmetic.
        }
    }

    private static boolean nearColumnBoundary(JTableHeader header, int x) {
        TableColumnModel cm = header.getColumnModel();
        int edge = 0;
        for (int i = 0; i < cm.getColumnCount(); i++) {
            edge += cm.getColumn(i).getWidth();
            if (Math.abs(x - edge) <= RESIZE_ZONE_PX) {
                return true;
            }
        }
        return false;
    }
}
