package consulo.turtlecommander.ui;

import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.fileEditor.FileEditorManager;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.turtlecommander.filetype.TurtleCommanderNotifications;
import consulo.turtlecommander.action.FileManagerAction;
import consulo.turtlecommander.dialog.DriveSpaceDialogDescriptor;
import consulo.turtlecommander.model.DriveInfo;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.service.FileOperationService;
import consulo.turtlecommander.service.TabState;
import consulo.turtlecommander.settings.ResolvedStyle;
import consulo.turtlecommander.util.DateTimeFormatters;
import consulo.turtlecommander.util.FileUtils;
import consulo.turtlecommander.vfs.VfsStackEntry;
import consulo.turtlecommander.vfs.VirtualFileSystem;
import consulo.ui.Component;
import consulo.ui.HasFocus;
import consulo.ui.HorizontalAlignment;
import consulo.ui.style.StandardColors;
import consulo.ui.Table;
import consulo.ui.TextAttribute;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.ShortcutSet;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import consulo.application.dumb.DumbAware;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.TextItemPresentation;
import consulo.ui.Tree;
import consulo.ui.TreeExecutor;
import consulo.ui.TreeModel;
import consulo.ui.TreeNode;
import consulo.ui.TreeTable;
import consulo.ui.ex.action.ActionToolbar;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.ToggleAction;
import consulo.ui.image.Image;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import consulo.turtlecommander.action.EdtAction;
import consulo.turtlecommander.action.UnifiedPaneAction;
import consulo.ui.Card;
import consulo.ui.CardStyle;
import consulo.ui.CheckBox;
import consulo.ui.ImageBox;
import consulo.ui.Length;
import consulo.ui.Space;
import consulo.ui.image.ImageEffects;
import consulo.ui.layout.TileLayout;
import consulo.application.util.concurrent.AppExecutorUtil;
import java.util.HashMap;
import java.util.Locale;
import consulo.util.dataholder.Key;
import consulo.dataContext.UiDataProvider;
import consulo.turtlecommander.action.ActionTargets;
import consulo.turtlecommander.action.FileContextMenuState;
import consulo.ui.event.ContextMenuEvent;
import consulo.ui.event.details.InputDetails;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionPopupMenu;
import consulo.ui.clipboard.DataTransfer;
import consulo.ui.clipboard.DataTransferType;
import java.io.File;
import consulo.turtlecommander.service.ColorizationService;
import consulo.ui.color.ColorValue;
import java.util.concurrent.ConcurrentHashMap;
import consulo.ui.DragAndDropTransferHandler;
import consulo.ui.HasTransferHandler;
import consulo.virtualFileSystem.archive.ArchiveNioFileSystemManager;
import consulo.virtualFileSystem.archive.ArchivePath;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.turtlecommander.vfs.VfsPaths;

/**
 * One directory listing in the unified UI: the path bar, the table of entries, the quick filter and the status line.
 *
 * <p>Listing a directory, statting a typed path and resolving a file for the editor all block, so they run on a pooled
 * thread and come back through the {@link UIAccess} of the UI the tab was built in - on the web that is the browser
 * session, not a global event thread.
 */
public final class UnifiedFileTab implements Disposable, FileManagerTab {
    public static final Key<UnifiedFileTab> KEY = Key.create(UnifiedFileTab.class);

    private static final Logger LOG = Logger.getInstance(UnifiedFileTab.class);

    private static final String ACTION_ID_PREFIX = "TurtleCommander.";

    private static final int THUMBNAIL_TILE_WIDTH = 120;
    private static final int THUMBNAIL_SIZE = 64;
    /** A folder of thousands of entries would be thousands of cards; past this, the rest is left to the other views. */
    private static final int MAX_THUMBNAILS = 500;
    /** Larger images are shown by their icon - their bytes would all go over the wire to the browser. */
    private static final long MAX_THUMBNAIL_FILE_SIZE = 8L * 1024 * 1024;
    private static final long DOUBLE_CLICK_MILLIS = 500;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg", "ico");

    private static final String FILE_CONTEXT_MENU_GROUP = "TurtleCommander.FileContextMenu";

    private static final String VIEW_MODE_PLACE = "TurtleCommander.ViewMode";

    /** Lists a folder opened in the tree on a pooled thread; the tree applies the result on its UI. */
    private static final TreeExecutor POOLED_EXECUTOR = new TreeExecutor() {
        @Override
        public <T> CompletableFuture<T> execute(Tree<?> tree, Supplier<T> task) {
            CompletableFuture<T> result = new CompletableFuture<>();
            Application.get().executeOnPooledThread(() -> {
                try {
                    result.complete(task.get());
                }
                catch (Throwable e) {
                    result.completeExceptionally(e);
                }
            });
            return result;
        }
    };

    private static final TextAttribute MARKED = new TextAttribute(TextAttribute.STYLE_BOLD, StandardColors.RED);


    private final Project myProject;
    private final FileOperationService myFileOps;
    private final UIAccess myUIAccess;
    private final TabState myState;

    private final UnifiedPathField myPathField = new UnifiedPathField();
    private final MutableFlatDataModel<FileEntry> myModel = FlatDataModel.of(List.of());
    private final Table<FileEntry> myTable = Table.create(myModel);
    /** The brief view: the same entries, by name only. */
    private final Table<FileEntry> myListTable = Table.create(myModel);
    /** Built the first time the tree view is shown. */
    private @Nullable TreeTable<FileEntry> myTree;
    /** What the tree's root lists, read by its model off the UI thread - the model of the tables is not. */
    private volatile List<FileEntry> myShownEntries = List.of();
    private ViewMode myViewMode;
    /**
     * The color rule styles of the entries shown, resolved with each listing; the tree adds the entries of the folders
     * it opens, from its own thread.
     */
    private volatile Map<Path, ResolvedStyle> myStyles = new ConcurrentHashMap<>();

    /** The thumbnail view: a card per entry, laid out in tiles - built anew with every listing while it is shown. */
    private final TileLayout myThumbnails = TileLayout.create(Length.ofPixel(THUMBNAIL_TILE_WIDTH), Space.SMALL);
    private final Map<Path, Card> myThumbnailCards = new HashMap<>();
    private final Map<Path, CheckBox> myThumbnailChecks = new HashMap<>();
    private @Nullable FileEntry myThumbnailCursor;
    /** A card clicked twice within the double-click time opens - the cards report single clicks only. */
    private @Nullable FileEntry myLastClicked;
    private long myLastClickTime;
    private final QuickFilterBar myQuickFilterBar;
    private final QuickFilterMatcher myFilterMatcher = new QuickFilterMatcher();
    private final UnifiedStatusBar myStatusBar = new UnifiedStatusBar();
    private final DockLayout myRoot = DockLayout.create();

    private final Runnable myUnsubscribeRoots;

    /** The entries an operation acts on, Insert-style; separate from the cursor, which is the table selection. */
    private final Set<Path> myMarkedPaths = new LinkedHashSet<>();
    private Set<Path> mySavedMarks = Set.of();
    private final ReentrantLock myVfsWriteLock = new ReentrantLock();
    /** Bumped per listing request, so that a slow listing which finishes after a newer one is dropped. */
    private final AtomicInteger myNavigationSeq = new AtomicInteger();

    private Path myCurrentPath;
    private List<FileEntry> myAllEntries = List.of();
    private @Nullable Consumer<UnifiedFileTab> myOnLocationChanged;
    /**
     * Whether the listing, rather than the path edit box or the quick filter, is what is being worked in - the keymap
     * actions act only then, so that typing a space or a plus into a box does not mark files. The frontends do not all
     * report the focus of a component, so it is followed from where the input goes.
     */
    private boolean myViewActive = true;
    private @Nullable UnifiedFilePane myPane;
    private Supplier<@Nullable UnifiedFileTab> myOtherPanelTab = () -> null;
    private Consumer<UnifiedFileTab> myOnActivated = tab -> {
    };

    @RequiredUIAccess
    public UnifiedFileTab(Project project, TabState state, Path initialPath) {
        myProject = project;
        myFileOps = project.getInstance(FileOperationService.class);
        myUIAccess = UIAccess.current();
        myState = state;
        myCurrentPath = initialPath;
        myViewMode = supportedMode(ViewMode.fromName(state.viewMode));

        setUpTable();
        setUpListTable();
        myTable.setRowBackgroundGetter(this::backgroundOf);
        myListTable.setRowBackgroundGetter(this::backgroundOf);
        installDragAndDrop(myTable);
        installDragAndDrop(myListTable);
        registerShortcuts(myTable);
        registerShortcuts(myListTable);
        // the keys typed with a card's checkbox focused bubble up to the tiles
        registerShortcuts(myThumbnails);

        myQuickFilterBar = new QuickFilterBar(text -> {
            applyFilter();
            if (myViewMode == ViewMode.TREE) {
                refreshTree(null);
            }
            else if (myViewMode == ViewMode.THUMBNAIL) {
                rebuildThumbnails(null);
            }
        }, this::hideQuickFilter, this::moveSelection, this::focusTable);

        myPathField.setOnSegmentClick(text -> navigateToTyped(text));
        myPathField.setOnSubmit(this::navigateToTyped);
        myPathField.setOnEditModeChanged(editing -> setViewActive(!editing));
        myPathField.setOnRootChosen(root -> navigateTo(root.path()));
        myUnsubscribeRoots = myFileOps.subscribeToRoots(roots -> myUIAccess.give(() -> myPathField.setRoots(roots)));

        // stat'ing a dead mount can block for seconds, so the volumes are read off the UI thread
        myStatusBar.setOnFreeSpaceClick(() -> Application.get().executeOnPooledThread(() -> {
            List<DriveInfo> drives = DriveInfo.collectAll(myFileOps.collectRoots());
            myUIAccess.give(() -> DriveSpaceDialogDescriptor.show(myProject, drives));
        }));

        VerticalLayout bottom = VerticalLayout.create();
        bottom.add(myQuickFilterBar.getComponent());
        bottom.add(myStatusBar.getComponent());

        DockLayout top = DockLayout.create();
        top.center(myPathField.getComponent());
        top.right(createViewModeToolbar());
        // the table runs to the pane's edges; the path bar keeps a margin, like the status line under it
        top.paddingBuilder().horizontalSet(Space.MEDIUM).apply();

        // the tab and its pane for the actions - the tab context menu reads them off the tab's content
        myRoot.putUserData(UiDataProvider.KEY, sink -> {
            sink.set(KEY, this);
            sink.set(UnifiedFilePane.KEY, myPane);
            sink.set(Project.KEY, myProject);
        });
        myRoot.setDropHandler(this::dropFiles);
        myRoot.top(top);
        myRoot.center(viewComponent(myViewMode));
        myRoot.bottom(bottom);

        navigateTo(initialPath);
    }

    public Component getComponent() {
        return myRoot;
    }

    @Override
    public Path getCurrentPath() {
        return myCurrentPath;
    }

    /** Called with this tab whenever it lands on another directory - the pane retitles the tab and saves the path. */
    public void setOnLocationChanged(@Nullable Consumer<UnifiedFileTab> onLocationChanged) {
        myOnLocationChanged = onLocationChanged;
    }

    @RequiredUIAccess
    private void setUpTable() {
        // one row is the cursor; what an operation acts on is marked, not selected
        myTable.setAllowMultipleSelect(false);
        // the frontends do not stretch a column to the free width, so the name, which is what the listing is read by,
        // starts out wide
        myTable.addColumn(LocalizeValue.localizeTODO("Name"), entry -> entry).setWidth(260)
            .setRenderer((presentation, value, item) -> renderName(item, presentation));
        myTable.addColumn(LocalizeValue.localizeTODO("Ext"), UnifiedFileTab::extension).setWidth(60);
        myTable.addColumn(LocalizeValue.localizeTODO("Size"), UnifiedFileTab::size)
            .setWidth(90)
            .setHorizontalAlignment(HorizontalAlignment.RIGHT);
        myTable.addColumn(LocalizeValue.localizeTODO("Modified"), UnifiedFileTab::modified).setWidth(150);

        listenToTable(myTable);
    }

    @RequiredUIAccess
    private void setUpListTable() {
        myListTable.setAllowMultipleSelect(false);
        myListTable.setShowHeader(false);
        myListTable.addColumn(LocalizeValue.localizeTODO("Name"), entry -> entry).setWidth(400)
            .setRenderer((presentation, value, item) -> renderName(item, presentation));
        listenToTable(myListTable);
    }

    @RequiredUIAccess
    private void listenToTable(Table<FileEntry> table) {
        table.addDoubleClickListener(event -> {
            FileEntry entry = event.getValue();
            if (entry != null) {
                open(entry);
            }
        });
        table.addSelectListener(event -> {
            setViewActive(true);
            updateStatusBar();
        });
        // Enter, Backspace, Insert, Ctrl+S and the rest are the plugin's actions, bound to the view; handling them here
        // as well would run them twice. A key in the view only tells that the view is what is being worked in.
        table.addKeyPressedListener(event -> setViewActive(true));
        table.addContextMenuListener(this::onContextMenu);
    }

    /**
     * The name with its icon, in the color and weight of the color rule that matched it, bold red while marked - the
     * same in every view. A rule's strikethrough has no unified text attribute and is left out.
     */
    private void renderName(FileEntry item, TextItemPresentation presentation) {
        ResolvedStyle style = styleOf(item);
        presentation.withIcon(FileEntryIconUtil.imageFor(item, !style.isEmpty(), style));
        String name = item.parentLink() ? ".." : item.name();
        if (myMarkedPaths.contains(item.path())) {
            presentation.append(name, MARKED);
            return;
        }
        ColorValue color = UnifiedStatusBar.toColorValue(FileStyleUtil.fontColorOf(style));
        Integer fontStyle = style.fontStyle();
        presentation.append(name, color == null && fontStyle == null
            ? TextAttribute.REGULAR
            : new TextAttribute(fontStyle != null ? fontStyle : TextAttribute.STYLE_PLAIN, color));
    }

    private ResolvedStyle styleOf(FileEntry entry) {
        return entry.parentLink() ? ResolvedStyle.EMPTY : myStyles.getOrDefault(entry.path(), ResolvedStyle.EMPTY);
    }

    private @Nullable ColorValue backgroundOf(FileEntry entry) {
        return UnifiedStatusBar.toColorValue(FileStyleUtil.backgroundColorOf(styleOf(entry)));
    }

    /**
     * The color rules matching {@code entries}. Resolved off the UI thread: a rule on what a folder contains reads
     * that folder.
     */
    private static void resolveStyles(List<FileEntry> entries, Map<Path, ResolvedStyle> into) {
        ColorizationService colorization = ColorizationService.getInstance();
        for (FileEntry entry : entries) {
            if (entry.parentLink()) {
                continue;
            }
            ResolvedStyle style = colorization.resolve(entry);
            if (!style.isEmpty()) {
                into.put(entry.path(), style);
            }
        }
    }

    // --- View modes ----------------------------------------------------------------------------------

    private static ViewMode supportedMode(ViewMode mode) {
        return mode;
    }

    @RequiredUIAccess
    private Component createViewModeToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new ViewModeAction(ViewMode.TABLE, "Table View", PlatformIconGroup.actionsPreviewdetails()));
        group.add(new ViewModeAction(ViewMode.LIST, "List View", PlatformIconGroup.actionsListfiles()));
        group.add(new ViewModeAction(ViewMode.THUMBNAIL, "Thumbnail View", PlatformIconGroup.actionsChangeview()));
        group.add(new ViewModeAction(ViewMode.TREE, "Tree View", PlatformIconGroup.actionsShowastree()));

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(VIEW_MODE_PLACE, group, true);
        toolbar.setTargetUIComponent(myRoot);
        return toolbar.getUIComponent();
    }

    /** The pane the tab is in; set by the pane when it adds the tab. */
    public void setPane(UnifiedFilePane pane) {
        myPane = pane;
    }

    public ViewMode getViewMode() {
        return myViewMode;
    }

    /** Shows the entries in {@code mode}, keeping the cursor on the entry it was on, and saves the mode with the tab. */
    @RequiredUIAccess
    public void setViewMode(ViewMode mode) {
        ViewMode supported = supportedMode(mode);
        if (supported == myViewMode) {
            return;
        }
        FileEntry cursor = getSelectedEntry();
        myViewMode = supported;
        myState.viewMode = supported.name();
        myRoot.center(viewComponent(supported));
        if (supported == ViewMode.TREE) {
            refreshTree(cursor != null ? cursor.name() : null);
        }
        else if (supported == ViewMode.THUMBNAIL) {
            rebuildThumbnails(cursor);
        }
        else if (cursor != null && myModel.indexOf(cursor) >= 0) {
            setCursor(cursor);
        }
        focusTable();
    }

    @RequiredUIAccess
    private Component viewComponent(ViewMode mode) {
        return switch (mode) {
            case LIST -> ScrollableLayout.create(myListTable);
            case TREE -> ScrollableLayout.create(tree());
            case THUMBNAIL -> ScrollableLayout.create(myThumbnails);
            default -> ScrollableLayout.create(myTable);
        };
    }

    /** The component of the current view - the one the cursor and the focus are in. */
    @RequiredUIAccess
    private Component activeView() {
        return switch (myViewMode) {
            case LIST -> myListTable;
            case TREE -> tree();
            case THUMBNAIL -> {
                // a card takes no focus, its checkbox does
                FileEntry cursor = myThumbnailCursor;
                CheckBox check = cursor != null ? myThumbnailChecks.get(cursor.path()) : null;
                yield check != null ? check : myThumbnails;
            }
            default -> myTable;
        };
    }

    @RequiredUIAccess
    private TreeTable<FileEntry> tree() {
        TreeTable<FileEntry> tree = myTree;
        if (tree != null) {
            return tree;
        }
        tree = TreeTable.create(new EntryTreeModel(), POOLED_EXECUTOR);
        tree.setTreeColumnHeader(LocalizeValue.localizeTODO("Name"));
        tree.addColumn(LocalizeValue.localizeTODO("Ext"), UnifiedFileTab::extension).setWidth(60);
        tree.addColumn(LocalizeValue.localizeTODO("Size"), UnifiedFileTab::size)
            .setWidth(90)
            .setHorizontalAlignment(HorizontalAlignment.RIGHT);
        tree.addColumn(LocalizeValue.localizeTODO("Modified"), UnifiedFileTab::modified).setWidth(150);
        tree.addSelectListener(event -> {
            setViewActive(true);
            updateStatusBar();
        });
        tree.addKeyPressedListener(event -> setViewActive(true));
        tree.addContextMenuListener(this::onContextMenu);
        myTree = tree;
        registerShortcuts(tree);
        return tree;
    }

    /** Lists the root of the tree again - the expanded folders are read anew as they are opened. */
    @RequiredUIAccess
    private void refreshTree(@Nullable String selectName) {
        TreeTable<FileEntry> tree = tree();
        tree.refreshAll().whenComplete((result, error) -> myUIAccess.give(() -> {
            TreeNode<FileEntry> root = tree.getRootNode();
            if (root == null) {
                return;
            }
            List<TreeNode<FileEntry>> children = root.getLoadedChildren();
            TreeNode<FileEntry> toSelect = null;
            for (TreeNode<FileEntry> child : children) {
                FileEntry value = child.getValue();
                if (value != null && selectName != null && !value.parentLink() && value.name().equals(selectName)) {
                    toSelect = child;
                    break;
                }
            }
            if (toSelect == null && !children.isEmpty()) {
                toSelect = children.get(0);
            }
            if (toSelect != null) {
                tree.select(toSelect);
            }
        }));
    }

    /** The node of {@code entry} among the ones the tree has built, or null. */
    private @Nullable TreeNode<FileEntry> findTreeNode(TreeTable<FileEntry> tree, FileEntry entry) {
        TreeNode<FileEntry> root = tree.getRootNode();
        return root == null ? null : findTreeNode(root, entry.path());
    }

    private static @Nullable TreeNode<FileEntry> findTreeNode(TreeNode<FileEntry> parent, Path path) {
        for (TreeNode<FileEntry> child : parent.getLoadedChildren()) {
            FileEntry value = child.getValue();
            if (value == null) {
                continue;
            }
            if (value.path().equals(path) && !value.parentLink()) {
                return child;
            }
            if (value.directory() && path.startsWith(value.path())) {
                TreeNode<FileEntry> found = findTreeNode(child, path);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Steps over the rows the tree shows: the listing, and the children of the folders that are expanded. */
    @RequiredUIAccess
    private void moveTreeCursor(int offset) {
        TreeTable<FileEntry> tree = tree();
        TreeNode<FileEntry> root = tree.getRootNode();
        if (root == null) {
            return;
        }
        Set<TreeNode<FileEntry>> expanded = new HashSet<>();
        for (List<TreeNode<FileEntry>> path : tree.getExpandedPaths()) {
            if (!path.isEmpty()) {
                expanded.add(path.get(path.size() - 1));
            }
        }
        List<TreeNode<FileEntry>> rows = new ArrayList<>();
        collectVisibleRows(root, expanded, rows);
        if (rows.isEmpty()) {
            return;
        }
        int index = rows.indexOf(tree.getSelectedNode());
        int target = Math.max(0, Math.min(rows.size() - 1, index + offset));
        tree.select(rows.get(target));
    }

    private static void collectVisibleRows(TreeNode<FileEntry> parent, Set<TreeNode<FileEntry>> expanded,
                                           List<TreeNode<FileEntry>> into) {
        for (TreeNode<FileEntry> child : parent.getLoadedChildren()) {
            into.add(child);
            if (expanded.contains(child)) {
                collectVisibleRows(child, expanded, into);
            }
        }
    }

    /** The entries of the folders opened in the tree, below the listing itself. */
    private static void collectExpandedEntries(TreeNode<FileEntry> parent, List<FileEntry> into) {
        for (TreeNode<FileEntry> child : parent.getLoadedChildren()) {
            FileEntry value = child.getValue();
            if (value != null) {
                into.add(value);
                collectExpandedEntries(child, into);
            }
        }
    }

    /** The entry under the cursor of the current view, or null; may be the ".." row. */
    @RequiredUIAccess
    private @Nullable FileEntry cursorEntry() {
        return switch (myViewMode) {
            case LIST -> myListTable.getSelectedItem();
            case TREE -> {
                TreeNode<FileEntry> node = tree().getSelectedNode();
                yield node != null ? node.getValue() : null;
            }
            case THUMBNAIL -> myThumbnailCursor;
            default -> myTable.getSelectedItem();
        };
    }

    @RequiredUIAccess
    private void setCursor(FileEntry entry) {
        switch (myViewMode) {
            case LIST -> {
                myListTable.select(entry);
                myListTable.scrollTo(entry);
            }
            case TREE -> {
                TreeTable<FileEntry> tree = tree();
                TreeNode<FileEntry> node = findTreeNode(tree, entry);
                if (node != null) {
                    tree.select(node);
                }
            }
            case THUMBNAIL -> setThumbnailCursor(entry);
            default -> {
                myTable.select(entry);
                myTable.scrollTo(entry);
            }
        }
    }

    // --- Thumbnails ---------------------------------------------------------------------------------

    /** A card per shown entry, the cursor kept on {@code cursor} when it is still there, else on the first. */
    @RequiredUIAccess
    private void rebuildThumbnails(@Nullable FileEntry cursor) {
        myThumbnails.removeAll();
        myThumbnailCards.clear();
        myThumbnailChecks.clear();
        myThumbnailCursor = null;

        FileEntry first = null;
        FileEntry kept = null;
        int count = 0;
        for (FileEntry entry : myModel) {
            if (count++ >= MAX_THUMBNAILS) {
                break;
            }
            myThumbnails.add(createThumbnailCard(entry));
            if (first == null) {
                first = entry;
            }
            if (cursor != null && entry.path().equals(cursor.path()) && entry.parentLink() == cursor.parentLink()) {
                kept = entry;
            }
        }
        FileEntry toSelect = kept != null ? kept : first;
        if (toSelect != null) {
            setThumbnailCursor(toSelect);
        }
    }

    @RequiredUIAccess
    private Card createThumbnailCard(FileEntry entry) {
        ImageBox image = ImageBox.create(thumbnailImage(entry));

        Card card = Card.create();
        card.addStyle(CardStyle.OUTLINED);
        card.addStyle(CardStyle.COMPACT);
        card.addStyle(CardStyle.CENTERED);
        card.setMedia(image);
        card.setTitle(LocalizeValue.of(entry.parentLink() ? ".." : entry.name()));
        if (!entry.parentLink()) {
            // the card's choice is the mark - the way a card standing for a choice carries the control making it
            CheckBox check = CheckBox.create(LocalizeValue.empty());
            check.setValue(myMarkedPaths.contains(entry.path()), false);
            check.addValueListener(event -> {
                if (Boolean.TRUE.equals(event.getValue())) {
                    myMarkedPaths.add(entry.path());
                }
                else {
                    myMarkedPaths.remove(entry.path());
                }
                updateStatusBar();
            });
            card.setHeaderPrefix(check);
            myThumbnailChecks.put(entry.path(), check);
        }
        card.addClickListener(event -> onThumbnailClick(entry));
        card.addContextMenuListener(event -> {
            setViewActive(true);
            setThumbnailCursor(entry);
            onContextMenu(event);
        });
        myThumbnailCards.put(entry.path(), card);
        return card;
    }

    @RequiredUIAccess
    private void onThumbnailClick(FileEntry entry) {
        long now = System.currentTimeMillis();
        boolean second = entry.equals(myLastClicked) && now - myLastClickTime < DOUBLE_CLICK_MILLIS;
        myLastClicked = second ? null : entry;
        myLastClickTime = now;

        setViewActive(true);
        setThumbnailCursor(entry);
        if (second) {
            open(entry);
        }
    }

    @RequiredUIAccess
    private void setThumbnailCursor(FileEntry entry) {
        FileEntry previous = myThumbnailCursor;
        if (previous != null) {
            Card previousCard = myThumbnailCards.get(previous.path());
            if (previousCard != null) {
                previousCard.removeStyle(CardStyle.SELECTED);
            }
        }
        myThumbnailCursor = entry;
        Card card = myThumbnailCards.get(entry.path());
        if (card != null) {
            card.addStyle(CardStyle.SELECTED);
        }
        updateStatusBar();
    }

    /** The icon of the type, large; an image file shows itself, read off the UI thread once the card is shown. */
    private static Image thumbnailImage(FileEntry entry) {
        Image icon = ImageEffects.resize(FileEntryIconUtil.imageFor(entry, false, ResolvedStyle.EMPTY), THUMBNAIL_SIZE);
        String extension = extension(entry).toLowerCase(Locale.ROOT);
        if (entry.directory() || entry.parentLink() || !IMAGE_EXTENSIONS.contains(extension) || entry.size() > MAX_THUMBNAIL_FILE_SIZE) {
            return icon;
        }
        Path path = entry.path();
        return Image.async(icon, () -> CompletableFuture.supplyAsync(() -> {
            try {
                Image image = Image.fromBytes(Image.ImageType.fromExtension(extension), Files.readAllBytes(path));
                return ImageEffects.resize(image, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
            }
            catch (Exception e) {
                return null;
            }
        }, AppExecutorUtil.getAppExecutorService()));
    }

    /** A toggle of the mode toolbar, on while its mode is shown. */
    private final class ViewModeAction extends ToggleAction implements DumbAware {
        private final ViewMode myMode;

        ViewModeAction(ViewMode mode, String text, Image icon) {
            super(LocalizeValue.localizeTODO(text), LocalizeValue.empty(), icon);
            myMode = mode;
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
            return myViewMode == myMode;
        }

        @RequiredUIAccess
        @Override
        public void setSelected(AnActionEvent e, boolean state) {
            if (state) {
                setViewMode(myMode);
            }
        }
    }

    /**
     * The tree's root is the listing as shown - filtered, without re-reading the disk; a folder opened in it is
     * listed on a pooled thread, through {@link #POOLED_EXECUTOR}.
     */
    private final class EntryTreeModel implements TreeModel<FileEntry> {
        @Override
        public void buildChildren(Function<FileEntry, TreeNode<FileEntry>> nodeFactory, @Nullable FileEntry parentValue) {
            List<FileEntry> children;
            if (parentValue == null) {
                children = myShownEntries;
            }
            else {
                children = new ArrayList<>();
                try {
                    for (FileEntry entry : myFileOps.listFiles(parentValue.path())) {
                        if (!entry.parentLink()) {
                            children.add(entry);
                        }
                    }
                }
                catch (Exception e) {
                    LOG.warn("Cannot list " + parentValue.path(), e);
                }
            }
            if (parentValue != null) {
                resolveStyles(children, myStyles);
            }
            for (FileEntry child : children) {
                TreeNode<FileEntry> node = nodeFactory.apply(child);
                node.setLeaf(child.parentLink() || !child.directory());
                node.setRenderer((value, presentation) -> renderName(value, presentation));
            }
        }

        /** A folder opens in place; a file opens in the editor and ".." goes up, as in the other views. */
        @Override
        public boolean onDoubleClick(Tree<FileEntry> tree, TreeNode<FileEntry> node) {
            FileEntry entry = node.getValue();
            if (entry == null || entry.directory() && !entry.parentLink()) {
                return true;
            }
            myUIAccess.give(() -> open(entry));
            return false;
        }
    }

    /**
     * The plugin's actions carry their own default shortcuts, which no keymap holds - so they are bound to the table,
     * where they are meant to work and nowhere else: bound any higher, they would take the keys typed into the path box
     * and the quick filter too. The browser frontend sends the server only the keys bound somewhere, a keymap or a
     * component like this.
     */
    @RequiredUIAccess
    private void registerShortcuts(Component view) {
        ActionManager actionManager = ActionManager.getInstance();
        for (String id : actionManager.getActionIds(ACTION_ID_PREFIX)) {
            AnAction action = actionManager.getAction(id);
            if (!(action instanceof FileManagerAction || action instanceof UnifiedPaneAction)
                || !(action instanceof EdtAction edtAction)) {
                continue;
            }
            // a binding the user gave it in the keymap wins over the default
            if (action.getShortcutSet().getShortcuts().length > 0) {
                action.registerCustomShortcutSet(view, this);
            }
            else {
                ShortcutSet defaultShortcut = edtAction.getDefaultShortcutSet();
                if (defaultShortcut != null) {
                    action.registerCustomShortcutSet(defaultShortcut, view, this);
                }
            }
        }
    }

    /**
     * Rows drag out as a file list, which the other pane - or anything taking files - drops; a drop on a folder row
     * copies into that folder. A platform whose tables take no transfer handler yet keeps the drop on the whole tab.
     */
    @SuppressWarnings("unchecked")
    @RequiredUIAccess
    private void installDragAndDrop(Table<FileEntry> table) {
        if ((Object) table instanceof HasTransferHandler<?> transferable) {
            ((HasTransferHandler<FileEntry>) transferable).setTransferHandler(new EntryTransferHandler());
        }
    }

    private final class EntryTransferHandler implements DragAndDropTransferHandler<FileEntry> {
        @Override
        public @Nullable DataTransfer createTransfer(Component component) {
            return transferOf(getSelectedEntries());
        }

        @Override
        public @Nullable DataTransfer createDragTransfer(Component component, List<FileEntry> items, boolean move) {
            return transferOf(items);
        }

        @RequiredUIAccess
        @Override
        public boolean drop(Component component, DropContext<FileEntry> context) {
            FileEntry target = context.getTarget();
            Path destination = context.getPosition() == DropPosition.INTO && target.directory() && !target.parentLink()
                ? target.path()
                : myCurrentPath;
            List<File> files = context.getTransfer().get(DataTransferType.FILE_LIST);
            if (files == null || files.isEmpty() || allIn(files, destination)) {
                return false;
            }
            return context.isCheckOnly() || copyInto(files, destination);
        }

        private static @Nullable DataTransfer transferOf(List<FileEntry> entries) {
            List<File> files = new ArrayList<>(entries.size());
            for (FileEntry entry : entries) {
                if (!entry.parentLink()) {
                    files.add(entry.path().toFile());
                }
            }
            return files.isEmpty() ? null : DataTransfer.builder().put(DataTransferType.FILE_LIST, files).build();
        }

        /** Dropping files on the folder they are in would copy them onto themselves. */
        private static boolean allIn(List<File> files, Path directory) {
            for (File file : files) {
                if (!directory.equals(file.toPath().getParent())) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Files dropped on the tab are copied into its folder, through the copy dialog like the Swing tab's drop - from
     * outside the IDE, or from the other pane, which drags its entries as a file list too.
     */
    @RequiredUIAccess
    private boolean dropFiles(Component component, DataTransfer transfer, boolean checkOnly) {
        List<File> files = transfer.get(DataTransferType.FILE_LIST);
        if (files == null || files.isEmpty()) {
            return false;
        }
        if (checkOnly) {
            return true;
        }
        return copyInto(files, myCurrentPath);
    }

    /** Copies {@code files} into {@code destination} through the copy dialog. */
    @RequiredUIAccess
    private boolean copyInto(List<File> files, Path destination) {
        // stat'ing the dropped files can block on a network share, so the entries are built off the UI thread
        Application.get().executeOnPooledThread(() -> {
            List<FileEntry> entries = new ArrayList<>(files.size());
            for (File file : files) {
                entries.add(FileEntry.builder(file.getName(), file.toPath())
                    .directory(file.isDirectory())
                    .size(file.isFile() ? file.length() : 0)
                    .build());
            }
            myUIAccess.give(() -> FileTabOperationUtil.performCopyEntries(this, entries, destination, destination.toString()));
        });
        return true;
    }

    @RequiredUIAccess
    private void open(FileEntry entry) {
        if (entry.directory() || entry.parentLink()) {
            navigateTo(entry.path());
            return;
        }
        Path path = entry.path();
        Application.get().executeOnPooledThread(() -> {
            // an archive opens as a folder, through the platform's archive file systems - read only
            ArchivePath archiveRoot = ArchiveNioFileSystemManager.getInstance().getRoot(path);
            if (archiveRoot != null) {
                myUIAccess.give(() -> navigateTo(archiveRoot));
                return;
            }
            VirtualFile file = path instanceof ArchivePath archivePath
                ? VirtualFileManager.getInstance().findFileByUrl(archivePath.getVirtualFileUrl())
                : LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
            if (file != null) {
                myUIAccess.give(() -> FileEditorManager.getInstance(myProject).openFile(file, true));
            }
        });
    }

    /** The root of an archive has no parent of its own - its ".." leaves the archive for the archive's folder. */
    private static List<FileEntry> withArchiveParent(Path directory, List<FileEntry> entries) {
        Path archiveParent = archiveParentOf(directory);
        if (archiveParent == null) {
            return entries;
        }
        List<FileEntry> withParent = new ArrayList<>(entries.size() + 1);
        withParent.add(VfsPaths.parentEntry(archiveParent));
        withParent.addAll(entries);
        return withParent;
    }

    /** The folder an archive's root goes up to, or null when {@code path} is not the root of an archive. */
    private static @Nullable Path archiveParentOf(Path path) {
        return path instanceof ArchivePath archivePath && archivePath.getEntryPath().isEmpty()
            ? archivePath.getArchiveFile().getParent()
            : null;
    }

    /** The folder on disk: the one holding the archive while inside one. */
    private static Path realDirectoryOf(Path path) {
        if (path instanceof ArchivePath archivePath) {
            Path parent = archivePath.getArchiveFile().getParent();
            return parent != null ? parent : archivePath.getArchiveFile();
        }
        return path;
    }

    /** Inside an archive, the archive's path, "!/" and the entry's path - the way the platform names archive entries. */
    private static String displayPathOf(Path path) {
        if (path instanceof ArchivePath archivePath) {
            return archivePath.getArchiveFile() + "!/" + archivePath.getEntryPath();
        }
        return path.toString();
    }

    @Override
    public void goUp() {
        Path archiveParent = archiveParentOf(myCurrentPath);
        Path parent = archiveParent != null ? archiveParent : myCurrentPath.getParent();
        if (parent != null) {
            navigateTo(parent);
        }
    }

    /** A typed or clicked path; a file or a missing path lands on its nearest existing directory. */
    private void navigateToTyped(String text) {
        Path typed;
        try {
            typed = Path.of(text);
        }
        catch (Exception e) {
            return;
        }
        if (!typed.isAbsolute()) {
            return;
        }
        Application.get().executeOnPooledThread(() -> {
            Path path = typed;
            while (path != null && !Files.isDirectory(path)) {
                path = path.getParent();
            }
            if (path != null) {
                Path target = path;
                myUIAccess.give(() -> navigateTo(target));
            }
        });
    }

    /** Lists {@code directory} off the UI thread and shows it, keeping the current listing until it arrives. */
    @Override
    public void navigateTo(Path directory, @Nullable String selectName) {
        int seq = myNavigationSeq.incrementAndGet();
        Application.get().executeOnPooledThread(() -> {
            List<FileEntry> entries;
            try {
                entries = withArchiveParent(directory, myFileOps.listFiles(directory));
            }
            catch (Exception e) {
                // the directory went away - deleted or moved from here, the other pane or outside: the nearest one
                // still there is shown instead, the way a file manager follows a removed folder
                Path existing = directory.getParent();
                while (existing != null && !Files.isDirectory(existing)) {
                    existing = existing.getParent();
                }
                if (!Files.isDirectory(directory) && existing != null) {
                    Path fallback = existing;
                    myUIAccess.give(() -> navigateTo(fallback, null));
                    return;
                }
                LOG.warn("Cannot list " + directory, e);
                myUIAccess.give(() -> fileErrorNotification("Cannot open " + directory + ": " + e.getMessage()));
                return;
            }
            String freeSpace = StatusBarTextUtil.freeSpace(realDirectoryOf(directory));
            Map<Path, ResolvedStyle> styles = new ConcurrentHashMap<>();
            resolveStyles(entries, styles);
            myUIAccess.give(() -> {
                if (seq == myNavigationSeq.get()) {
                    myStyles = styles;
                    applyListing(directory, entries, freeSpace, selectName);
                }
            });
        });
    }

    @RequiredUIAccess
    private void applyListing(Path directory, List<FileEntry> entries, String freeSpace, @Nullable String selectName) {
        // leaving an archive selects the archive file, as going up selects the folder just left
        Path previous = myCurrentPath instanceof ArchivePath archivePath && archivePath.getEntryPath().isEmpty()
            ? archivePath.getArchiveFile()
            : myCurrentPath;
        boolean sameDirectory = directory.equals(myCurrentPath);
        // a re-listing keeps the cursor and the marks that still exist; another directory starts clean
        FileEntry cursor = sameDirectory ? cursorEntry() : null;
        String cursorName = selectName != null ? selectName : cursor != null && !cursor.parentLink() ? cursor.name() : null;
        if (sameDirectory) {
            Set<Path> present = new HashSet<>();
            for (FileEntry entry : entries) {
                present.add(entry.path());
            }
            myMarkedPaths.retainAll(present);
        }
        else {
            myMarkedPaths.clear();
        }

        myCurrentPath = directory;
        myAllEntries = entries;
        // an archive is not reopened at startup, its folder is
        myState.path = realDirectoryOf(directory).toString();

        myPathField.setText(displayPathOf(directory));
        myStatusBar.setFreeSpace(freeSpace);
        if (myQuickFilterBar.isVisible() && !sameDirectory) {
            myQuickFilterBar.deactivate();
        }
        applyFilter();

        FileEntry toSelect = null;
        for (FileEntry entry : myModel) {
            if (entry.parentLink()) {
                continue;
            }
            // by name when asked or re-listing; going up selects the directory that was just left
            if (cursorName != null ? entry.name().equals(cursorName) : entry.path().equals(previous)) {
                toSelect = entry;
                break;
            }
        }
        if (toSelect == null && myModel.getSize() > 0) {
            toSelect = myModel.get(0);
        }
        if (myViewMode == ViewMode.TREE) {
            refreshTree(toSelect != null ? toSelect.name() : null);
        }
        else if (myViewMode == ViewMode.THUMBNAIL) {
            rebuildThumbnails(toSelect);
        }
        else if (toSelect != null) {
            setCursor(toSelect);
        }

        Consumer<UnifiedFileTab> onLocationChanged = myOnLocationChanged;
        if (!sameDirectory && onLocationChanged != null) {
            onLocationChanged.accept(this);
        }
    }

    @RequiredUIAccess
    private void applyFilter() {
        QuickFilterMatcher.Result result = myFilterMatcher.filter(myAllEntries, myQuickFilterBar.getText());
        myQuickFilterBar.setError(result.invalidPattern());
        myModel.replaceAll(result.entries());
        myShownEntries = List.copyOf(result.entries());
        updateStatusBar();
    }

    @RequiredUIAccess
    private void hideQuickFilter() {
        myQuickFilterBar.deactivate();
        applyFilter();
        focusTable();
    }

    /** Every frontend's table takes the focus, though the API does not say so. */
    @RequiredUIAccess
    private void focusTable() {
        setViewActive(true);
        if (activeView() instanceof HasFocus focusable) {
            focusable.focus();
        }
    }

    @RequiredUIAccess
    private void moveSelection(int offset) {
        int size = myModel.getSize();
        if (size == 0) {
            return;
        }
        if (myViewMode == ViewMode.TREE) {
            moveTreeCursor(offset);
            return;
        }
        FileEntry selected = cursorEntry();
        int index = selected == null ? -1 : myModel.indexOf(selected);
        int target = Math.max(0, Math.min(size - 1, index + offset));
        setCursor(myModel.get(target));
    }

    @RequiredUIAccess
    private void updateStatusBar() {
        List<FileEntry> shown = new ArrayList<>(myModel.getSize());
        for (FileEntry entry : myModel) {
            shown.add(entry);
        }
        myStatusBar.setSummary(StatusBarTextUtil.summary(shown, getSelectedEntries(), Map.of()));
    }

    // --- FileManagerTab ------------------------------------------------------------------------------

    /** Wires the tab to the rest of the view: which tab is the other pane's, and what to tell when this one is used. */
    public void setPanelLinks(Supplier<@Nullable UnifiedFileTab> otherPanelTab, Consumer<UnifiedFileTab> onActivated) {
        myOtherPanelTab = otherPanelTab;
        myOnActivated = onActivated;
    }

    @Override
    public Project getProject() {
        return myProject;
    }

    @Override
    public UIAccess getUIAccess() {
        return myUIAccess;
    }

    @Override
    public FileOperationService getFileOps() {
        return myFileOps;
    }

    /** The unified tab does not enter archives yet. */
    @Override
    public @Nullable VirtualFileSystem getCurrentVfs() {
        return null;
    }

    @Override
    public boolean isInsideArchive() {
        return myCurrentPath instanceof ArchivePath;
    }

    @Override
    public Path getRealFilesystemPath() {
        return myCurrentPath instanceof ArchivePath archivePath ? archivePath.getArchiveFile() : myCurrentPath;
    }

    @Override
    public List<VfsStackEntry> getVfsStack() {
        return List.of();
    }

    @Override
    public ReentrantLock getVfsWriteLock() {
        return myVfsWriteLock;
    }

    @Override
    public @Nullable Path getOtherPanelPath() {
        UnifiedFileTab other = myOtherPanelTab.get();
        return other != null ? other.getCurrentPath() : null;
    }

    @Override
    public @Nullable FileManagerTab getOtherPanelTab() {
        return myOtherPanelTab.get();
    }

    @Override
    public @Nullable String getOtherPanelDisplayPath() {
        FileManagerTab other = getOtherPanelTab();
        return other != null ? other.getDisplayPath() : null;
    }

    @Override
    public void fileErrorNotification(String content) {
        TurtleCommanderNotifications.error(myProject, content);
    }

    /** The free space is read anew with every listing, so there is nothing cached to drop. */
    @Override
    public void invalidateFreeSpaceCache() {
    }

    @Override
    public List<FileEntry> getSelectedEntries() {
        if (!myMarkedPaths.isEmpty()) {
            List<FileEntry> known = new ArrayList<>(myAllEntries);
            TreeTable<FileEntry> tree = myTree;
            TreeNode<FileEntry> root = myViewMode == ViewMode.TREE && tree != null ? tree.getRootNode() : null;
            if (root != null) {
                collectExpandedEntries(root, known);
            }
            Map<Path, FileEntry> marked = new LinkedHashMap<>();
            for (FileEntry entry : known) {
                if (!entry.parentLink() && myMarkedPaths.contains(entry.path())) {
                    marked.putIfAbsent(entry.path(), entry);
                }
            }
            return new ArrayList<>(marked.values());
        }
        FileEntry cursor = cursorEntry();
        return cursor == null || cursor.parentLink() ? List.of() : List.of(cursor);
    }

    @Override
    public @Nullable FileEntry getSelectedEntry() {
        return cursorEntry();
    }

    @Override
    public List<FileEntry> getDisplayedEntries() {
        List<FileEntry> shown = new ArrayList<>(myModel.getSize());
        for (FileEntry entry : myModel) {
            shown.add(entry);
        }
        return shown;
    }

    @Override
    public Set<Path> getMarkedPaths() {
        return myMarkedPaths;
    }

    @Override
    public void refresh() {
        myFileOps.invalidateListingCache(myCurrentPath);
        navigateTo(myCurrentPath, null);
    }

    @RequiredUIAccess
    @Override
    public void focusActiveView() {
        focusTable();
    }

    /** The Turtle editor holding the focus is checked by the action targeting; this tells which part of it. */
    @Override
    public boolean hasAnyViewFocus() {
        return myViewActive;
    }

    private void setViewActive(boolean viewActive) {
        myViewActive = viewActive;
        if (viewActive) {
            myOnActivated.accept(this);
        }
    }

    @RequiredUIAccess
    @Override
    public void showQuickFilter() {
        setViewActive(false);
        myQuickFilterBar.activate();
    }

    @RequiredUIAccess
    @Override
    public void startRename() {
        FileTabOperationUtil.startRename(this);
    }

    @Override
    public String getDisplayPath() {
        return displayPathOf(myCurrentPath);
    }

    @Override
    public void goToRoot() {
        Path root = myCurrentPath.getRoot();
        if (root != null && !root.equals(myCurrentPath)) {
            navigateTo(root);
        }
    }

    @RequiredUIAccess
    @Override
    public void openSelectedEntry() {
        FileEntry entry = cursorEntry();
        if (entry != null) {
            open(entry);
        }
    }

    @RequiredUIAccess
    @Override
    public void selectFirstRow() {
        moveCursorTo(0);
    }

    @RequiredUIAccess
    @Override
    public void selectLastRow() {
        moveCursorTo(myModel.getSize() - 1);
    }

    @RequiredUIAccess
    @Override
    public void selectAllEntries() {
        for (FileEntry entry : myModel) {
            if (!entry.parentLink()) {
                myMarkedPaths.add(entry.path());
            }
        }
        marksChanged();
    }

    @RequiredUIAccess
    @Override
    public void unselectAllEntries() {
        myMarkedPaths.clear();
        marksChanged();
    }

    @RequiredUIAccess
    @Override
    public void invertSelection() {
        for (FileEntry entry : myModel) {
            if (!entry.parentLink() && !myMarkedPaths.remove(entry.path())) {
                myMarkedPaths.add(entry.path());
            }
        }
        marksChanged();
    }

    @RequiredUIAccess
    @Override
    public void toggleSelection() {
        FileEntry cursor = cursorEntry();
        if (cursor != null && !cursor.parentLink() && !myMarkedPaths.remove(cursor.path())) {
            myMarkedPaths.add(cursor.path());
        }
        marksChanged();
    }

    @RequiredUIAccess
    @Override
    public void toggleSelectionAndMoveDown() {
        toggleSelection();
        moveSelection(1);
    }

    @RequiredUIAccess
    @Override
    public void saveSelection() {
        mySavedMarks = Set.copyOf(myMarkedPaths);
    }

    @RequiredUIAccess
    @Override
    public void restoreSavedSelection() {
        myMarkedPaths.clear();
        for (FileEntry entry : myAllEntries) {
            if (mySavedMarks.contains(entry.path())) {
                myMarkedPaths.add(entry.path());
            }
        }
        marksChanged();
    }

    @RequiredUIAccess
    @Override
    public void markByMask(String mask, boolean select, boolean includeDirectories) {
        List<PathMatcher> patterns = FileTabSelectionUtil.parseSelectionMaskPatterns(mask);
        for (FileEntry entry : myModel) {
            if (entry.parentLink() || entry.directory() && !includeDirectories || !matches(entry.name(), patterns)) {
                continue;
            }
            if (select) {
                myMarkedPaths.add(entry.path());
            }
            else {
                myMarkedPaths.remove(entry.path());
            }
        }
        marksChanged();
    }

    @RequiredUIAccess
    @Override
    public void markSameExtension(boolean select) {
        FileEntry cursor = cursorEntry();
        if (cursor == null || cursor.parentLink() || cursor.directory()) {
            return;
        }
        String ext = extension(cursor);
        for (FileEntry entry : myModel) {
            if (entry.parentLink() || entry.directory() || !extension(entry).equalsIgnoreCase(ext)) {
                continue;
            }
            if (select) {
                myMarkedPaths.add(entry.path());
            }
            else {
                myMarkedPaths.remove(entry.path());
            }
        }
        marksChanged();
    }

    /** From the keyboard - Shift+F10 or the menu key - where there is no click to place the menu at. */
    @RequiredUIAccess
    @Override
    public void showContextMenu() {
        showFileContextMenu(activeView(), 0, 0);
    }

    /** The views move their cursor to the row clicked before the event, so the entry under it is the cursor's. */
    @RequiredUIAccess
    private void onContextMenu(ContextMenuEvent event) {
        setViewActive(true);
        InputDetails details = event.getInputDetails();
        showFileContextMenu(event.getComponent(), details.getX(), details.getY());
    }

    @RequiredUIAccess
    private void showFileContextMenu(Component anchor, int x, int y) {
        ActionManager actionManager = ActionManager.getInstance();
        if (!(actionManager.getAction(FILE_CONTEXT_MENU_GROUP) instanceof ActionGroup group)) {
            return;
        }
        FileContextMenuState.setClickedEntry(cursorEntry());
        FileContextMenuState.setClickedTab(this);

        ActionPopupMenu menu = actionManager.createActionPopupMenu(ActionTargets.FILE_CONTEXT_PLACE, group);
        menu.setTargetComponent(activeView());
        menu.show(anchor, x, y);
    }

    private static boolean matches(String name, List<PathMatcher> patterns) {
        Path asPath = Path.of(name);
        for (PathMatcher pattern : patterns) {
            if (pattern.matches(asPath)) {
                return true;
            }
        }
        return false;
    }

    @RequiredUIAccess
    private void moveCursorTo(int index) {
        if (index < 0 || index >= myModel.getSize()) {
            return;
        }
        setCursor(myModel.get(index));
    }

    /** Redraws the names, which show the marks, and the summary, which counts them. */
    @RequiredUIAccess
    private void marksChanged() {
        for (FileEntry entry : myModel) {
            myModel.update(entry);
        }
        TreeTable<FileEntry> tree = myTree;
        TreeNode<FileEntry> root = myViewMode == ViewMode.TREE && tree != null ? tree.getRootNode() : null;
        if (root != null) {
            refreshLoaded(tree, root);
        }
        for (Map.Entry<Path, CheckBox> check : myThumbnailChecks.entrySet()) {
            check.getValue().setValue(myMarkedPaths.contains(check.getKey()), false);
        }
        updateStatusBar();
    }

    private static void refreshLoaded(TreeTable<FileEntry> tree, TreeNode<FileEntry> parent) {
        for (TreeNode<FileEntry> child : parent.getLoadedChildren()) {
            tree.refreshItem(child);
            refreshLoaded(tree, child);
        }
    }

    private static String extension(FileEntry entry) {
        String name = entry.name();
        if (entry.parentLink() || entry.directory() || name.indexOf('.') < 0 || name.startsWith(".")) {
            return "";
        }
        return StringUtil.notNullize(StringUtil.substringAfterLast(name, "."));
    }

    private static String size(FileEntry entry) {
        if (entry.parentLink()) {
            return "";
        }
        return entry.directory() ? "<DIR>" : FileUtils.formatSize(entry.size());
    }

    private static String modified(FileEntry entry) {
        FileTime time = entry.lastModified();
        return entry.parentLink() || time == null ? "" : DateTimeFormatters.format(time.toMillis());
    }

    @Override
    public void dispose() {
        myUnsubscribeRoots.run();
    }
}
