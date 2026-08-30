package consulo.turtlecommander.ui;

import consulo.disposer.Disposable;
import consulo.application.progress.ProgressIndicator;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.turtlecommander.action.ActionTargets;
import consulo.turtlecommander.action.SearchContextMenuState;
import consulo.turtlecommander.dialog.FileSearchDialog;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.search.FileSearchCriteria;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.service.FileSearchService;
import consulo.turtlecommander.util.BackgroundProgress;
import consulo.turtlecommander.util.FileUtils;
import consulo.turtlecommander.util.UiTask;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionPopupMenu;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.awt.JBScrollPane;
import consulo.ui.ex.awt.speedSearch.TableSpeedSearch;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import org.jspecify.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A search-results tab: a read-only table that fills in as the walk proceeds, with pause, stop and
 * edit-search controls and the same quick filter the file panels have.
 */
public class SearchResultsPanel extends JPanel implements Disposable {
    /** Result batching: flush on whichever of these comes first, so the table stays responsive. */
    private static final long FLUSH_INTERVAL_MS = 200;
    private static final int FLUSH_BATCH_SIZE = 50;

    private final Project myProject;
    private FileSearchCriteria myCriteria;

    private final FileTableModel myTableModel = new FileTableModel();
    private final JBTable myTable = new JBTable(myTableModel);
    private final List<FileEntry> myResultEntries = new ArrayList<>();
    private final JLabel myStatusLabel = new JLabel("Ready");

    private final JButton myEditButton = new JButton("Edit Search",
        TargetAWT.to(PlatformIconGroup.actionsEdit()));
    private final JButton myPauseResumeButton = new JButton("Pause",
        TargetAWT.to(PlatformIconGroup.actionsPause()));
    private final JButton myStopButton = new JButton("Stop",
        TargetAWT.to(PlatformIconGroup.actionsCancel()));

    private final QuickFilterBar myQuickFilterBar;
    private final QuickFilterMatcher myFilterMatcher = new QuickFilterMatcher();

    private volatile @Nullable FileSearchService mySearchService;
    private volatile boolean myStopRequested;
    private volatile boolean myDisposed;

    public SearchResultsPanel(Project project, FileSearchCriteria criteria) {
        super(new BorderLayout());
        myProject = project;
        myCriteria = criteria;

        myQuickFilterBar = new QuickFilterBar(text -> applyFilter(), this::hideQuickFilter,
            this::moveTableSelection, myTable::requestFocusInWindow);

        setupTable();
        add(new JBScrollPane(myTable), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(myQuickFilterBar, BorderLayout.NORTH);
        bottomPanel.add(createControlPanel(), BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    // --- Table -------------------------------------------------------------------------------------

    private void setupTable() {
        myTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        myTable.setCellSelectionEnabled(false);
        myTable.setRowSelectionAllowed(true);
        myTable.setAutoResizeMode(JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        myTable.setRowHeight(20);
        // Search results are read-only — no inline rename here.
        myTable.setDefaultEditor(Object.class, null);

        TableRowSorter<FileTableModel> sorter = new TableRowSorter<>(myTableModel);
        // Size and the two timestamps are Longs in the model but rendered as formatted text, so
        // they need numeric comparators rather than the default string ordering.
        Comparator<Object> numeric = Comparator.comparingLong(
            value -> value instanceof Long number ? number : 0L);
        sorter.setComparator(FileTableModel.COL_SIZE, numeric);
        sorter.setComparator(FileTableModel.COL_CREATED, numeric);
        sorter.setComparator(FileTableModel.COL_DATE, numeric);
        myTable.setRowSorter(sorter);

        applyColumnLayout();
        installMouseHandling();

        new TableSpeedSearch(myTable, value -> value instanceof FileEntry entry
            ? entry.name()
            : value != null ? value.toString() : "");

        myTable.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openSelected");
        myTable.getActionMap().put("openSelected", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                navigateToSelected();
            }
        });

        // Ctrl+S is the IDE's global "Save All"; a component-local action outranks it, matching
        // how the file panels bind their quick filter.
        ListKeyBindingUtil.installShortcut(myTable,
            KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), this::showQuickFilter);
    }

    private void applyColumnLayout() {
        int[] widths = {300, 50, 80, 130, 130, 80};
        for (int i = 0; i < widths.length && i < myTable.getColumnModel().getColumnCount(); i++) {
            myTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            myTable.getColumnModel().getColumn(i)
                .setCellRenderer(i == 0 ? new SearchFileNameRenderer() : new SearchDisplayValueRenderer());
        }
    }

    private void installMouseHandling() {
        myTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelected();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handleContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleContextMenu(e);
            }
        });
    }

    private void handleContextMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int row = myTable.rowAtPoint(e.getPoint());
        if (row >= 0) {
            // Keep an existing multi-row selection when the right-click lands inside it; only
            // replace the selection when the user right-clicked an unselected row.
            if (!myTable.isRowSelected(row)) {
                myTable.setRowSelectionInterval(row, row);
            }
            SearchContextMenuState.setClickedEntry(
                myTableModel.getEntryAt(myTable.convertRowIndexToModel(row)));
        }
        else {
            SearchContextMenuState.setClickedEntry(null);
        }
        SearchContextMenuState.setSelectedEntries(selectedEntries());

        ActionManager actionManager = ActionManager.getInstance();
        AnAction action = actionManager.getAction(ActionTargets.SEARCH_CONTEXT_PLACE);
        if (!(action instanceof ActionGroup group)) {
            SearchContextMenuState.clear();
            return;
        }
        consulo.ui.Component target = TargetAWT.wrap(myTable);
        ActionPopupMenu popupMenu =
            actionManager.createActionPopupMenu(ActionTargets.SEARCH_CONTEXT_PLACE, group);
        popupMenu.setTargetComponent(target);
        popupMenu.show(target, e.getX(), e.getY());
    }

    private List<FileEntry> selectedEntries() {
        List<FileEntry> entries = new ArrayList<>();
        for (int viewRow : myTable.getSelectedRows()) {
            FileEntry entry = myTableModel.getEntryAt(myTable.convertRowIndexToModel(viewRow));
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /** Opens the hit's directory in a new tab of the opposite panel, with the hit selected. */
    private void navigateToSelected() {
        int row = myTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        FileEntry entry = myTableModel.getEntryAt(myTable.convertRowIndexToModel(row));
        if (entry == null) {
            return;
        }
        Path targetDir = entry.path().getParent();
        if (targetDir == null) {
            return;
        }

        FileManagerStateService stateService = FileManagerStateService.getInstance(myProject);
        FileManagerPanel activePanel = stateService.getActivePanel();
        FileManagerPanel otherPanel = activePanel == stateService.getLeftPanel()
            ? stateService.getRightPanel()
            : stateService.getLeftPanel();
        if (otherPanel != null) {
            otherPanel.openDirectoryInNewTab(targetDir, entry.name());
        }
    }

    private void moveTableSelection(int offset) {
        if (myTable.getRowCount() == 0) {
            return;
        }
        int next = Math.clamp(myTable.getSelectedRow() + offset, 0, myTable.getRowCount() - 1);
        myTable.setRowSelectionInterval(next, next);
        myTable.scrollRectToVisible(myTable.getCellRect(next, 0, true));
    }

    public void requestTableFocus() {
        myTable.requestFocusInWindow();
    }

    public boolean hasTableFocus() {
        return myTable.hasFocus();
    }

    // --- Quick filter -------------------------------------------------------------------------------

    public void showQuickFilter() {
        myQuickFilterBar.activate();
        revalidate();
    }

    private void hideQuickFilter() {
        myQuickFilterBar.deactivate();
        // deactivate() fires no document event when the field was already empty, so re-apply
        // explicitly to guarantee the unfiltered results come back.
        applyFilter();
        revalidate();
        myTable.requestFocusInWindow();
    }

    private void applyFilter() {
        QuickFilterMatcher.Result result = myFilterMatcher.filter(myResultEntries, myQuickFilterBar.getText());
        myQuickFilterBar.setError(result.invalidPattern());

        int selectedRow = myTable.getSelectedRow();
        FileEntry selected = selectedRow >= 0
            ? myTableModel.getEntryAt(myTable.convertRowIndexToModel(selectedRow))
            : null;
        Path selectedPath = selected != null ? selected.path() : null;

        myTableModel.setEntries(result.entries());
        restoreSelection(result.entries(), selectedPath);
    }

    private void restoreSelection(List<FileEntry> entries, @Nullable Path selectedPath) {
        if (selectedPath != null) {
            for (int modelIdx = 0; modelIdx < entries.size(); modelIdx++) {
                if (!entries.get(modelIdx).path().equals(selectedPath)) {
                    continue;
                }
                int viewIdx = myTable.convertRowIndexToView(modelIdx);
                if (viewIdx >= 0) {
                    myTable.setRowSelectionInterval(viewIdx, viewIdx);
                    myTable.scrollRectToVisible(myTable.getCellRect(viewIdx, 0, true));
                }
                return;
            }
        }
        if (myTable.getSelectedRow() < 0 && myTable.getRowCount() > 0) {
            myTable.setRowSelectionInterval(0, 0);
        }
    }

    private List<FileEntry> filteredEntries() {
        return myFilterMatcher.filter(myResultEntries, myQuickFilterBar.getText()).entries();
    }

    // --- Controls -----------------------------------------------------------------------------------

    private JPanel createControlPanel() {
        myEditButton.addActionListener(e -> editSearch());
        myPauseResumeButton.addActionListener(e -> togglePause());
        myStopButton.addActionListener(e -> stopSearch());
        myStopButton.setEnabled(false);
        myPauseResumeButton.setEnabled(false);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        buttonPanel.add(myEditButton);
        buttonPanel.add(myPauseResumeButton);
        buttonPanel.add(myStopButton);

        myStatusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttonPanel, BorderLayout.WEST);
        panel.add(myStatusLabel, BorderLayout.CENTER);
        return panel;
    }

    private void togglePause() {
        FileSearchService service = mySearchService;
        if (service == null) {
            return;
        }
        boolean paused = !service.isPaused();
        service.setPaused(paused);
        myPauseResumeButton.setText(paused ? "Resume" : "Pause");
        myPauseResumeButton.setIcon(TargetAWT.to(paused
            ? PlatformIconGroup.actionsExecute()
            : PlatformIconGroup.actionsPause()));
        myStatusLabel.setText(paused
            ? "Search paused. " + myResultEntries.size() + " files found."
            : "Searching... " + myResultEntries.size() + " files found");
    }

    private void stopSearch() {
        myStopRequested = true;
        FileSearchService service = mySearchService;
        if (service != null) {
            // A paused search would never reach its next cancellation check.
            service.setPaused(false);
        }
    }

    private void editSearch() {
        FileSearchDialog dialog = new FileSearchDialog(myProject, myCriteria.rootPath(), myCriteria);
        if (dialog.showAndGet()) {
            myCriteria = dialog.getCriteria();
            startSearch();
        }
    }

    // --- Search -------------------------------------------------------------------------------------

    public void startSearch() {
        if (myDisposed) {
            return;
        }
        myResultEntries.clear();
        myStopRequested = false;
        // Hide the quick filter on a new or edited search — stale criteria would silently hide
        // fresh results.
        if (myQuickFilterBar.isVisible()) {
            myQuickFilterBar.deactivate();
            revalidate();
        }
        myTableModel.setEntries(List.of());

        FileSearchService service = new FileSearchService(myCriteria);
        mySearchService = service;

        myStopButton.setEnabled(true);
        myPauseResumeButton.setEnabled(true);
        myPauseResumeButton.setText("Pause");
        myPauseResumeButton.setIcon(TargetAWT.to(PlatformIconGroup.actionsPause()));
        myEditButton.setEnabled(false);
        myStatusLabel.setText("Searching...");

        BackgroundProgress.run(myProject, LocalizeValue.localizeTODO("Searching files..."), indicator -> {
            ResultBuffer buffer = new ResultBuffer();
            try {
                service.search(buffer::add,
                    () -> indicator.isCanceled() || myStopRequested || myDisposed,
                    (scannedCount, currentDir) -> reportProgress(indicator, scannedCount, currentDir));
            }
            catch (FileSearchService.InvalidPatternException e) {
                String message = e.getMessage() != null ? e.getMessage() : "Invalid search pattern";
                UiTask.ui(() -> finishSearch(message));
                return;
            }
            catch (IOException e) {
                // The walk itself failed — an unreadable root, or the tree vanished mid-search.
                // Whatever was already found stays on screen.
                buffer.flush();
                UiTask.ui(() -> finishSearch("Search failed: " + FileUtils.fileErrorMessage(e)));
                return;
            }
            buffer.flush();

            boolean stopped = indicator.isCanceled() || myStopRequested;
            UiTask.ui(() -> finishSearch((stopped ? "Search stopped." : "Search complete.")
                + " " + myResultEntries.size() + " files found."));
        });
    }

    private void reportProgress(ProgressIndicator indicator, int scannedCount, String currentDir) {
        indicator.setText(LocalizeValue.localizeTODO("Scanned " + scannedCount + " entries..."));
        indicator.setText2(LocalizeValue.of(currentDir));
        if (myDisposed) {
            return;
        }
        UiTask.ui(() -> myStatusLabel.setText("Searching... " + myResultEntries.size()
            + " files found, scanned " + scannedCount + " entries"));
    }

    private void finishSearch(String status) {
        if (myDisposed) {
            return;
        }
        myStatusLabel.setText(status);
        myStopButton.setEnabled(false);
        myPauseResumeButton.setEnabled(false);
        myEditButton.setEnabled(true);
    }

    /**
     * Batches incoming hits so a fast search doesn't post one UI update per file. Results arrive on
     * the search thread and are handed to the UI thread in groups.
     */
    private final class ResultBuffer {
        private final List<FileEntry> myPending = new ArrayList<>();
        private long myLastFlush = System.currentTimeMillis();

        void add(FileEntry entry) {
            boolean shouldFlush;
            synchronized (myPending) {
                myPending.add(entry);
                long now = System.currentTimeMillis();
                shouldFlush = now - myLastFlush > FLUSH_INTERVAL_MS || myPending.size() >= FLUSH_BATCH_SIZE;
                if (shouldFlush) {
                    myLastFlush = now;
                }
            }
            if (shouldFlush) {
                flush();
            }
        }

        void flush() {
            List<FileEntry> batch;
            synchronized (myPending) {
                if (myPending.isEmpty()) {
                    return;
                }
                batch = List.copyOf(myPending);
                myPending.clear();
            }
            if (myDisposed) {
                return;
            }
            UiTask.ui(() -> appendResults(batch));
        }
    }

    private void appendResults(List<FileEntry> batch) {
        boolean wasEmpty = myResultEntries.isEmpty();
        myResultEntries.addAll(batch);

        int selectedRow = myTable.getSelectedRow();
        myTableModel.setEntries(filteredEntries());

        if (wasEmpty && myTable.getRowCount() > 0) {
            myTable.setRowSelectionInterval(0, 0);
            // Don't steal focus while the user is typing in the quick filter.
            if (isShowing() && !myQuickFilterBar.hasFieldFocus()) {
                myTable.requestFocusInWindow();
            }
        }
        else if (selectedRow >= 0 && selectedRow < myTable.getRowCount()) {
            myTable.setRowSelectionInterval(selectedRow, selectedRow);
        }
    }

    @Override
    public void dispose() {
        myDisposed = true;
        stopSearch();
    }

    // --- Renderers -----------------------------------------------------------------------------------

    /** The Name column: type icon, the full path as a tooltip, and speed-search highlighting. */
    private final class SearchFileNameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, @Nullable Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, false, row, column);

            FileEntry entry = myTableModel.getEntryAt(table.convertRowIndexToModel(row));
            setIcon(entry != null ? TargetAWT.to(iconFor(entry)) : null);
            setToolTipText(entry != null ? entry.path().toString() : null);
            setText(FileStyleUtil.highlightSpeedSearchMatches(table, value != null ? value.toString() : ""));
            return this;
        }

        private Image iconFor(FileEntry entry) {
            if (entry.directory()) {
                return PlatformIconGroup.nodesFolder();
            }
            Image icon = FileTypeRegistry.getInstance().getFileTypeByFileName(entry.name()).getIcon();
            return icon != null ? icon : PlatformIconGroup.filetypesAny_type();
        }
    }

    /** Every other column: the model's formatted display string rather than its raw value. */
    private final class SearchDisplayValueRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, @Nullable Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            String displayValue = myTableModel.getDisplayValue(table.convertRowIndexToModel(row),
                table.convertColumnIndexToModel(column));
            return super.getTableCellRendererComponent(table, displayValue, isSelected, false, row, column);
        }
    }
}
