package consulo.turtlecommander.ui;

import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.disposer.Disposable;
import consulo.fileEditor.FileEditorManager;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.search.FileSearchCriteria;
import consulo.turtlecommander.service.FileSearchService;
import consulo.turtlecommander.settings.ResolvedStyle;
import consulo.turtlecommander.util.BackgroundProgress;
import consulo.turtlecommander.util.DateTimeFormatters;
import consulo.turtlecommander.util.FileUtils;
import consulo.ui.Button;
import consulo.ui.Component;
import consulo.ui.HorizontalAlignment;
import consulo.ui.Label;
import consulo.ui.Space;
import consulo.ui.Table;
import consulo.ui.TextItemPresentation;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.event.details.KeyCode;
import consulo.ui.event.details.KeyboardInputDetails;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Search results in the unified UI: a read-only table that fills in as the walk proceeds, with pause, stop and
 * edit-search controls and the quick filter the file tabs have.
 *
 * <p>The walk runs on a pooled thread; its hits are batched and handed to the {@link UIAccess} of the UI the view was
 * built in - on the web that is the browser session, not a global event thread.
 */
public final class UnifiedSearchResultsView implements Disposable {
    /** Result batching: flush on whichever of these comes first, so the table stays responsive. */
    private static final long FLUSH_INTERVAL_MS = 200;
    private static final int FLUSH_BATCH_SIZE = 50;

    private final Project myProject;
    private final UIAccess myUIAccess;
    private final Consumer<FileEntry> myOnNavigate;
    private FileSearchCriteria myCriteria;

    private final MutableFlatDataModel<FileEntry> myModel = FlatDataModel.of(List.of());
    private final Table<FileEntry> myTable = Table.create(myModel);
    /** Every hit of the current search; the model holds those the quick filter lets through. */
    private final List<FileEntry> myResultEntries = new ArrayList<>();
    private final Label myStatusLabel = Label.create(LocalizeValue.localizeTODO("Ready"));

    private final Button myEditButton = Button.create(LocalizeValue.localizeTODO("Edit Search"));
    private final Button myPauseResumeButton = Button.create(LocalizeValue.localizeTODO("Pause"));
    private final Button myStopButton = Button.create(LocalizeValue.localizeTODO("Stop"));

    private final QuickFilterBar myQuickFilterBar;
    private final QuickFilterMatcher myFilterMatcher = new QuickFilterMatcher();
    private final DockLayout myRoot = DockLayout.create();

    private @Nullable Runnable myOnEditSearch;
    /** The search running or last run; a restart replaces it, and whatever the old one still delivers is dropped. */
    private volatile @Nullable SearchRun myRun;
    private volatile boolean myDisposed;

    @RequiredUIAccess
    public UnifiedSearchResultsView(Project project, FileSearchCriteria criteria, Consumer<FileEntry> onNavigate) {
        myProject = project;
        myUIAccess = UIAccess.current();
        myOnNavigate = onNavigate;
        myCriteria = criteria;

        myQuickFilterBar = new QuickFilterBar(text -> applyFilter(), this::hideQuickFilter, this::moveSelection,
            myTable::focus);

        setUpTable();

        VerticalLayout bottom = VerticalLayout.create();
        bottom.add(myQuickFilterBar.getComponent());
        bottom.add(createControls());

        myRoot.center(ScrollableLayout.create(myTable));
        myRoot.bottom(bottom);
    }

    public Component getComponent() {
        return myRoot;
    }

    public FileSearchCriteria getCriteria() {
        return myCriteria;
    }

    /** Edit Search hands over to the owner, which shows the dialog and comes back through {@link #restart}. */
    public void setOnEditSearch(@Nullable Runnable onEditSearch) {
        myOnEditSearch = onEditSearch;
    }

    // --- Table -------------------------------------------------------------------------------------

    @RequiredUIAccess
    private void setUpTable() {
        myTable.setAllowMultipleSelect(false);
        // hits from many folders - the name says what was found, the folder where
        myTable.addColumn(LocalizeValue.localizeTODO("Name"), entry -> entry).setWidth(260)
            .setRenderer((presentation, value, item) -> renderName(item, presentation));
        myTable.addColumn(LocalizeValue.localizeTODO("Folder"), UnifiedSearchResultsView::folder).setWidth(300);
        // the value is the entry so it sorts by bytes, not by the formatted text
        myTable.addColumn(LocalizeValue.localizeTODO("Size"), entry -> entry)
            .setWidth(90)
            .setHorizontalAlignment(HorizontalAlignment.RIGHT)
            .setRenderer((presentation, value, item) -> presentation.append(size(item)))
            .setSortable(Comparator.comparingLong(entry -> entry.directory() ? -1L : entry.size()));
        myTable.addColumn(LocalizeValue.localizeTODO("Modified"), entry -> entry)
            .setWidth(150)
            .setRenderer((presentation, value, item) -> presentation.append(modified(item)))
            .setSortable(Comparator.comparingLong(UnifiedSearchResultsView::modifiedMillis));
        myTable.setSpeedSearchConverter(FileEntry::name);

        myTable.addDoubleClickListener(event -> {
            FileEntry entry = event.getValue();
            if (entry != null) {
                open(entry);
            }
        });
        // no actions are bound to the results, so the keys are handled here
        myTable.addKeyPressedListener(event -> {
            KeyboardInputDetails details = event.getInputDetails();
            KeyCode keyCode = details.getKeyCode();
            if (keyCode == null) {
                return;
            }
            int key = keyCode.key();
            if (key == KeyCode.ENTER.key()) {
                FileEntry entry = myTable.getSelectedItem();
                if (entry != null) {
                    open(entry);
                }
            }
            else if (details.withCtrl() && (key == 'S' || key == 's')) {
                showQuickFilter();
            }
        });
    }

    private static void renderName(FileEntry item, TextItemPresentation presentation) {
        presentation.withIcon(FileEntryIconUtil.imageFor(item, false, ResolvedStyle.EMPTY));
        presentation.append(item.name());
    }

    /** A file opens in the editor, resolved off the UI thread; a directory is revealed in a file tab. */
    @RequiredUIAccess
    private void open(FileEntry entry) {
        if (entry.directory()) {
            myOnNavigate.accept(entry);
            return;
        }
        Path path = entry.path();
        Application.get().executeOnPooledThread(() -> {
            VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
            if (file != null) {
                myUIAccess.give(() -> FileEditorManager.getInstance(myProject).openFile(file, true));
            }
        });
    }

    @RequiredUIAccess
    private void moveSelection(int offset) {
        int size = myModel.getSize();
        if (size == 0) {
            return;
        }
        FileEntry selected = myTable.getSelectedItem();
        int index = selected == null ? -1 : myModel.indexOf(selected);
        select(myModel.get(Math.max(0, Math.min(size - 1, index + offset))));
    }

    @RequiredUIAccess
    private void select(FileEntry entry) {
        myTable.select(entry);
        myTable.scrollTo(entry);
    }

    // --- Quick filter -------------------------------------------------------------------------------

    @RequiredUIAccess
    public void showQuickFilter() {
        myQuickFilterBar.activate();
    }

    @RequiredUIAccess
    private void hideQuickFilter() {
        myQuickFilterBar.deactivate();
        // deactivate() fires nothing when the field was already empty, so the unfiltered results are put back here
        applyFilter();
        myTable.focus();
    }

    /** Shows the hits the filter lets through, keeping the cursor on its entry when that is still shown. */
    @RequiredUIAccess
    private void applyFilter() {
        FileEntry selected = myTable.getSelectedItem();
        QuickFilterMatcher.Result result = myFilterMatcher.filter(myResultEntries, myQuickFilterBar.getText());
        myQuickFilterBar.setError(result.invalidPattern());
        myModel.replaceAll(result.entries());

        if (selected != null && myModel.indexOf(selected) >= 0) {
            myTable.select(selected);
        }
        else if (myModel.getSize() > 0) {
            myTable.select(myModel.get(0));
        }
    }

    // --- Controls -----------------------------------------------------------------------------------

    @RequiredUIAccess
    private Component createControls() {
        myEditButton.setIcon(PlatformIconGroup.actionsEdit());
        myPauseResumeButton.setIcon(PlatformIconGroup.actionsPause());
        myStopButton.setIcon(PlatformIconGroup.actionsCancel());

        myEditButton.addClickListener(event -> {
            Runnable onEditSearch = myOnEditSearch;
            if (onEditSearch != null) {
                onEditSearch.run();
            }
        });
        myPauseResumeButton.addClickListener(event -> togglePause());
        myStopButton.addClickListener(event -> stopSearch());
        myPauseResumeButton.setEnabled(false);
        myStopButton.setEnabled(false);

        HorizontalLayout buttons = HorizontalLayout.create(Space.X_SMALL);
        buttons.add(myEditButton);
        buttons.add(myPauseResumeButton);
        buttons.add(myStopButton);

        // the status takes what the buttons leave
        DockLayout controls = DockLayout.create();
        controls.left(buttons).center(myStatusLabel);
        controls.paddingBuilder().verticalSet(Space.X_SMALL).horizontalSet(Space.SMALL).apply();
        return controls;
    }

    @RequiredUIAccess
    private void togglePause() {
        SearchRun run = myRun;
        if (run == null || run.myFinished) {
            return;
        }
        boolean paused = !run.myService.isPaused();
        run.myService.setPaused(paused);
        myPauseResumeButton.setText(LocalizeValue.localizeTODO(paused ? "Resume" : "Pause"));
        myPauseResumeButton.setIcon(paused ? PlatformIconGroup.actionsExecute() : PlatformIconGroup.actionsPause());
        setStatus(paused
            ? "Search paused. " + myResultEntries.size() + " files found."
            : "Searching... " + myResultEntries.size() + " files found");
    }

    /** Safe from any thread: only flags the run, which ends at its next cancellation check. */
    private void stopSearch() {
        SearchRun run = myRun;
        if (run != null) {
            run.stop();
        }
    }

    @RequiredUIAccess
    private void setStatus(String text) {
        myStatusLabel.setText(LocalizeValue.of(text));
    }

    // --- Search -------------------------------------------------------------------------------------

    /** Searches again with new criteria - what Edit Search ends in. */
    @RequiredUIAccess
    public void restart(FileSearchCriteria criteria) {
        myCriteria = criteria;
        startSearch();
    }

    @RequiredUIAccess
    public void startSearch() {
        if (myDisposed) {
            return;
        }
        // a restart while the previous walk still runs must not mix its hits in
        stopSearch();

        myResultEntries.clear();
        // stale filter text would silently hide fresh results
        if (myQuickFilterBar.isVisible()) {
            myQuickFilterBar.deactivate();
        }
        myModel.removeAll();

        SearchRun run = new SearchRun(new FileSearchService(myCriteria));
        myRun = run;

        myStopButton.setEnabled(true);
        myPauseResumeButton.setEnabled(true);
        myPauseResumeButton.setText(LocalizeValue.localizeTODO("Pause"));
        myPauseResumeButton.setIcon(PlatformIconGroup.actionsPause());
        myEditButton.setEnabled(false);
        setStatus("Searching...");

        BackgroundProgress.run(myProject, LocalizeValue.localizeTODO("Searching files..."), indicator -> run.execute(indicator));
    }

    @RequiredUIAccess
    private void appendResults(SearchRun run, List<FileEntry> batch) {
        if (myDisposed || run != myRun) {
            return;
        }
        boolean wasEmpty = myResultEntries.isEmpty();
        myResultEntries.addAll(batch);

        QuickFilterMatcher.Result result = myFilterMatcher.filter(batch, myQuickFilterBar.getText());
        for (FileEntry entry : result.entries()) {
            myModel.add(entry);
        }
        if (wasEmpty && myModel.getSize() > 0 && myTable.getSelectedItem() == null) {
            myTable.select(myModel.get(0));
            // typing in the quick filter keeps its focus
            if (!myQuickFilterBar.hasFieldFocus()) {
                myTable.focus();
            }
        }
    }

    @RequiredUIAccess
    private void finishSearch(SearchRun run, String status) {
        if (myDisposed || run != myRun) {
            return;
        }
        run.myFinished = true;
        setStatus(status);
        myStopButton.setEnabled(false);
        myPauseResumeButton.setEnabled(false);
        myPauseResumeButton.setText(LocalizeValue.localizeTODO("Pause"));
        myPauseResumeButton.setIcon(PlatformIconGroup.actionsPause());
        myEditButton.setEnabled(true);
    }

    @Override
    public void dispose() {
        myDisposed = true;
        stopSearch();
    }

    /**
     * One walk: its service, its stop flag, and the batching of its hits. Hits arrive on the search thread and are
     * handed to the UI in groups, so a fast search does not post one UI update per file.
     */
    private final class SearchRun {
        private final FileSearchService myService;
        private final List<FileEntry> myPending = new ArrayList<>();
        private long myLastFlush = System.currentTimeMillis();
        private volatile boolean myStopRequested;
        /** Set on the UI thread once the outcome is shown. */
        private boolean myFinished;

        SearchRun(FileSearchService service) {
            myService = service;
        }

        void stop() {
            myStopRequested = true;
            // a paused walk would never reach its next cancellation check
            myService.setPaused(false);
        }

        void execute(ProgressIndicator indicator) {
            try {
                myService.search(this::add,
                    () -> indicator.isCanceled() || myStopRequested || myDisposed,
                    (scannedCount, currentDir) -> reportProgress(indicator, scannedCount, currentDir));
            }
            catch (FileSearchService.InvalidPatternException e) {
                String message = e.getMessage() != null ? e.getMessage() : "Invalid search pattern";
                myUIAccess.give(() -> finishSearch(this, message));
                return;
            }
            catch (IOException e) {
                // the walk itself failed - an unreadable root, or the tree vanished; what was found stays shown
                flush();
                myUIAccess.give(() -> finishSearch(this, "Search failed: " + FileUtils.fileErrorMessage(e)));
                return;
            }
            flush();

            boolean stopped = indicator.isCanceled() || myStopRequested;
            // the count is read on the UI thread, after the last batch it was given
            myUIAccess.give(() -> finishSearch(this, (stopped ? "Search stopped." : "Search complete.")
                + " " + myResultEntries.size() + " files found."));
        }

        private void reportProgress(ProgressIndicator indicator, int scannedCount, String currentDir) {
            indicator.setText(LocalizeValue.localizeTODO("Scanned " + scannedCount + " entries..."));
            indicator.setText2(LocalizeValue.of(currentDir));
            if (myDisposed) {
                return;
            }
            myUIAccess.give(() -> {
                // a paused or finished search keeps its own status
                if (myRun == this && !myFinished && !myService.isPaused() && !myDisposed) {
                    setStatus("Searching... " + myResultEntries.size() + " files found, scanned " + scannedCount + " entries");
                }
            });
        }

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
            myUIAccess.give(() -> appendResults(this, batch));
        }
    }

    // --- Columns ------------------------------------------------------------------------------------

    private static String folder(FileEntry entry) {
        Path parent = entry.path().getParent();
        return parent != null ? parent.toString() : "";
    }

    private static String size(FileEntry entry) {
        return entry.directory() ? "<DIR>" : FileUtils.formatSize(entry.size());
    }

    private static String modified(FileEntry entry) {
        FileTime time = entry.lastModified();
        return time == null ? "" : DateTimeFormatters.format(time.toMillis());
    }

    private static long modifiedMillis(FileEntry entry) {
        FileTime time = entry.lastModified();
        return time == null ? 0L : time.toMillis();
    }
}
