package consulo.turtlecommander.ui;

import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.turtlecommander.model.FileRoot;
import consulo.turtlecommander.model.Machine;
import consulo.turtlecommander.settings.ComponentStyle;
import consulo.ui.Breadcrumbs;
import consulo.ui.Button;
import consulo.ui.ButtonStyle;
import consulo.ui.Component;
import consulo.ui.LightPopup;
import consulo.ui.ListBox;
import consulo.ui.PopupOptions;
import consulo.ui.Rectangle2D;
import consulo.ui.Space;
import consulo.ui.TextBoxWithHistory;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.event.details.KeyCode;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.PlaceholderLayout;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The path bar: a breadcrumb trail of the current location, and an Edit button which swaps the trail for a text box
 * to type a path into. Enter submits the typed path, Escape - or the button again - goes back to the trail.
 *
 * <p>The trail starts at the root the location lives under - a drive, the home folder, a mounted volume - shown with
 * that root's name and icon. Clicking that first crumb lists every root to switch to, which is what a separate drive
 * selector used to do. Built on the unified UI, so it runs on every frontend; the trail collapses its middle when it
 * does not fit.
 *
 * <p>With other machines around - remote platforms - a machine button leads the bar: it names the machine the location
 * is on and switches to another one. The trail and the root list only offer the roots of that machine.
 */
public final class UnifiedPathField {
    /** Typed paths kept for the edit box's history, most recent first. */
    private static final int MAX_HISTORY = 20;

    /**
     * One crumb: what it shows, the path a click on it navigates to, and the root it stands for when it is the first
     * crumb of the trail.
     */
    record PathSegment(String name, String fullPath, @Nullable FileRoot root) {
        PathSegment(String name, String fullPath) {
            this(name, fullPath, null);
        }
    }

    private final Breadcrumbs<PathSegment> myCrumbs = Breadcrumbs.create();
    private final TextBoxWithHistory myEditBox = TextBoxWithHistory.create();
    private final PlaceholderLayout mySwitcher = PlaceholderLayout.create();
    private final Button myEditButton = Button.create(LocalizeValue.empty());
    private final Button myMachineButton = Button.create(LocalizeValue.empty());
    private final DockLayout myRoot = DockLayout.create(Space.NONE);

    private final List<String> myHistory = new ArrayList<>();
    private List<FileRoot> myRoots = List.of();
    private List<Machine> myMachines = List.of();
    /** The machine the location is on - its {@link FileRoot#location()}, empty for this machine. */
    private String myLocation = "";
    private String myText = "";
    private boolean myEditMode;
    private boolean myRootListEnabled = true;
    private @Nullable Consumer<String> myOnSegmentClick;
    private @Nullable Consumer<String> myOnSubmit;
    private @Nullable Consumer<FileRoot> myOnRootChosen;
    private @Nullable Consumer<Machine> myOnMachineChosen;
    /** The root list on screen, so a second click replaces it rather than stacking another one over it. */
    private @Nullable LightPopup myRootPopup;

    private @Nullable Consumer<Boolean> myOnEditModeChanged;

    @RequiredUIAccess
    public UnifiedPathField() {
        myCrumbs.setRenderer((presentation, item) -> {
            PathSegment segment = item.getValue();
            if (segment == null) {
                return;
            }
            FileRoot root = segment.root();
            if (root != null) {
                presentation.withIcon(root.icon());
            }
            presentation.append(segment.name());
            presentation.withTooltip(LocalizeValue.of(segment.fullPath()));
        });
        myCrumbs.addItemClickListener(event -> {
            PathSegment segment = event.getItem();
            if (segment.root() != null && myRootListEnabled && !machineRoots().isEmpty()) {
                showRoots(segment);
                return;
            }
            Consumer<String> onSegmentClick = myOnSegmentClick;
            if (onSegmentClick != null) {
                onSegmentClick.accept(segment.fullPath());
            }
        });

        myEditBox.addKeyPressedListener(event -> {
            KeyCode keyCode = event.getInputDetails().getKeyCode();
            if (keyCode == null) {
                return;
            }
            if (keyCode.key() == KeyCode.ENTER.key()) {
                submit();
            }
            else if (keyCode.key() == KeyCode.ESCAPE.key()) {
                setEditMode(false);
            }
        });

        mySwitcher.setContent(myCrumbs);
        mySwitcher.setPlaceholder(myEditBox);
        mySwitcher.setPlaceholderVisibility(() -> myEditMode);

        myEditButton.setIcon(PlatformIconGroup.actionsEdit());
        myEditButton.addStyle(ButtonStyle.BORDERLESS);
        myEditButton.setToolTipText(LocalizeValue.localizeTODO("Edit Path"));
        myEditButton.addClickListener(event -> setEditMode(!myEditMode));

        myMachineButton.addStyle(ButtonStyle.BORDERLESS);
        myMachineButton.setToolTipText(LocalizeValue.localizeTODO("Switch Machine"));
        myMachineButton.addClickListener(event -> showMachines());
        myMachineButton.setVisible(false);

        myRoot.left(myMachineButton).center(mySwitcher).right(myEditButton);
    }

    public Component getComponent() {
        return myRoot;
    }

    /** The trail's text, or what is being typed while editing. */
    public String getText() {
        return myEditMode ? String.valueOf(myEditBox.getValue()) : myText;
    }

    /** Shows {@code text} as the trail, leaving edit mode - a navigation replaces whatever was being typed. */
    @RequiredUIAccess
    public void setText(String text) {
        myText = text;
        myCrumbs.setItems(trail());
        setEditMode(false);
    }

    /** The roots the trail starts from and the first crumb offers. */
    @RequiredUIAccess
    public void setRoots(List<FileRoot> roots) {
        if (roots.equals(myRoots)) {
            return;
        }
        myRoots = List.copyOf(roots);
        myCrumbs.setItems(trail());
    }

    /** The machines to switch between - the button shows only when there is more than this one. */
    @RequiredUIAccess
    public void setMachines(List<Machine> machines) {
        if (machines.equals(myMachines)) {
            return;
        }
        myMachines = List.copyOf(machines);
        updateMachineButton();
    }

    /** The machine the location is on - see {@link FileRoot#location()}; set before the text of a new location. */
    @RequiredUIAccess
    public void setLocation(String location) {
        if (location.equals(myLocation)) {
            return;
        }
        myLocation = location;
        updateMachineButton();
        myCrumbs.setItems(trail());
    }

    /** Receives the machine picked from the machine button's list. */
    public void setOnMachineChosen(@Nullable Consumer<Machine> onMachineChosen) {
        myOnMachineChosen = onMachineChosen;
    }

    @RequiredUIAccess
    private void updateMachineButton() {
        Machine current = currentMachine();
        myMachineButton.setVisible(myMachines.size() > 1);
        myMachineButton.setText(current != null ? current.title() : LocalizeValue.of(myLocation));
        myMachineButton.setIcon(current != null ? current.icon() : null);
    }

    private @Nullable Machine currentMachine() {
        for (Machine machine : myMachines) {
            if (machine.location().equals(myLocation)) {
                return machine;
            }
        }
        return null;
    }

    @RequiredUIAccess
    private void showMachines() {
        ListBox<Machine> list = ListBox.create(myMachines);
        list.setRenderer((presentation, item) -> {
            Machine machine = item.getValue();
            if (machine == null) {
                return;
            }
            presentation.withIcon(machine.icon());
            presentation.append(machine.title());
        });
        list.setSelectOnHover(true);
        Machine current = currentMachine();
        if (current != null) {
            list.setValue(current, false);
        }

        LightPopup popup = LightPopup.create(PopupOptions.builder().build());
        popup.setContent(list);
        list.addClickListener(event -> {
            Machine chosen = list.getValue();
            popup.close();
            Consumer<Machine> onMachineChosen = myOnMachineChosen;
            if (chosen != null && onMachineChosen != null) {
                onMachineChosen.accept(chosen);
            }
        });
        popup.showBy(myMachineButton);
    }

    /** The roots of the machine the location is on - another machine's "/" is not this one's. */
    private List<FileRoot> machineRoots() {
        List<FileRoot> roots = new ArrayList<>();
        for (FileRoot root : myRoots) {
            if (root.location().equals(myLocation)) {
                roots.add(root);
            }
        }
        return roots;
    }

    /** Off, the first crumb navigates to its root like any other crumb instead of listing the roots. */
    public void setRootListEnabled(boolean enabled) {
        myRootListEnabled = enabled;
    }

    /** The theme's background; its font and text color have no unified counterpart on the trail yet. */
    @RequiredUIAccess
    public void applyStyle(ComponentStyle style) {
        myRoot.setBackgroundColor(UnifiedStatusBar.toColorValue(style.parsedBackgroundColor()));
    }

    /** Receives the full path of a clicked crumb. */
    public void setOnSegmentClick(@Nullable Consumer<String> onSegmentClick) {
        myOnSegmentClick = onSegmentClick;
    }

    /** Receives the typed text when Enter is pressed in edit mode. */
    public void setOnSubmit(@Nullable Consumer<String> onSubmit) {
        myOnSubmit = onSubmit;
    }

    /** Receives the root picked from the list the first crumb offers. */
    public void setOnRootChosen(@Nullable Consumer<FileRoot> onRootChosen) {
        myOnRootChosen = onRootChosen;
    }

    /** Opens the list of roots under the first crumb - what the drive selector shortcuts do. */
    /** Told {@code true} when the path turns into the edit box, {@code false} when it turns back into the crumbs. */
    public void setOnEditModeChanged(@Nullable Consumer<Boolean> onEditModeChanged) {
        myOnEditModeChanged = onEditModeChanged;
    }

    @RequiredUIAccess
    public void showRoots() {
        if (!myRootListEnabled || machineRoots().isEmpty()) {
            return;
        }
        List<PathSegment> items = myCrumbs.getItems();
        if (!items.isEmpty() && items.get(0).root() != null) {
            showRoots(items.get(0));
        }
    }

    @RequiredUIAccess
    private void showRoots(PathSegment rootSegment) {
        LightPopup previous = myRootPopup;
        if (previous != null) {
            myRootPopup = null;
            if (previous.isVisible()) {
                previous.close();
            }
        }

        ListBox<FileRoot> list = ListBox.create(machineRoots());
        list.setRenderer((presentation, item) -> {
            FileRoot root = item.getValue();
            if (root == null) {
                return;
            }
            presentation.withIcon(root.icon());
            presentation.append(root.presentableName());
        });
        list.setSelectOnHover(true);
        FileRoot current = rootSegment.root();
        if (current != null) {
            list.setValue(current, false);
        }

        LightPopup popup = LightPopup.create(PopupOptions.builder().build());
        popup.setContent(list);
        myRootPopup = popup;
        popup.addCloseListener(event -> {
            if (myRootPopup == popup) {
                myRootPopup = null;
            }
        });
        list.addClickListener(event -> {
            FileRoot chosen = list.getValue();
            popup.close();
            Consumer<FileRoot> onRootChosen = myOnRootChosen;
            if (chosen != null && onRootChosen != null) {
                onRootChosen.accept(chosen);
            }
        });

        Rectangle2D bounds = myCrumbs.getItemBounds(rootSegment);
        if (bounds != null) {
            popup.showAt(myCrumbs, bounds.minX(), bounds.minY(), bounds.size().height());
        }
        else {
            popup.showBy(myCrumbs);
        }
    }

    @RequiredUIAccess
    private void submit() {
        String typed = String.valueOf(myEditBox.getValue());
        setEditMode(false);
        if (!typed.isBlank()) {
            myHistory.remove(typed);
            myHistory.add(0, typed);
            if (myHistory.size() > MAX_HISTORY) {
                myHistory.remove(myHistory.size() - 1);
            }
            myEditBox.setHistory(myHistory);
        }
        Consumer<String> onSubmit = myOnSubmit;
        if (onSubmit != null) {
            onSubmit.accept(typed);
        }
    }

    @RequiredUIAccess
    private void setEditMode(boolean editMode) {
        if (myEditMode == editMode) {
            return;
        }
        myEditMode = editMode;
        if (editMode) {
            myEditBox.setValue(myText, false);
        }
        mySwitcher.testPlaceholder();
        if (editMode) {
            myEditBox.focus();
            myEditBox.selectAll();
        }
        Consumer<Boolean> onEditModeChanged = myOnEditModeChanged;
        if (onEditModeChanged != null) {
            onEditModeChanged.accept(editMode);
        }
    }

    /**
     * The crumbs of the current text, with everything down to the deepest known root folded into one crumb for that
     * root - an archive's text starts with the archive's own path, so it resolves the same way.
     */
    private List<PathSegment> trail() {
        List<PathSegment> segments = splitPath(myText);
        FileRoot best = null;
        int bestDepth = 0;
        for (FileRoot root : machineRoots()) {
            List<PathSegment> rootSegments = splitPath(root.path().toString());
            int depth = rootSegments.size();
            if (depth == 0 || depth > segments.size() || depth <= bestDepth) {
                continue;
            }
            if (segments.get(depth - 1).fullPath().equals(rootSegments.get(depth - 1).fullPath())) {
                best = root;
                bestDepth = depth;
            }
        }
        if (best == null) {
            return segments;
        }

        Path fileName = best.path().getFileName();
        String name = fileName != null ? fileName.toString() : best.presentableName();
        List<PathSegment> trail = new ArrayList<>(segments.size() - bestDepth + 1);
        trail.add(new PathSegment(name, segments.get(bestDepth - 1).fullPath(), best));
        trail.addAll(segments.subList(bestDepth, segments.size()));
        return trail;
    }

    static List<PathSegment> splitPath(String pathStr) {
        if (pathStr.isEmpty()) {
            return List.of();
        }

        List<PathSegment> segments = new ArrayList<>();
        boolean unc = pathStr.length() >= 2 && pathStr.charAt(0) == '\\' && pathStr.charAt(1) == '\\';
        boolean windows = pathStr.length() >= 2 && pathStr.charAt(1) == ':';

        if (unc) {
            // \\server\share\a\b -> one root crumb "\\server\share" (there is nothing above the
            // share), then a, b. A bare "\\server" with no share stays the root crumb.
            List<String> parts = nonEmptyParts(pathStr.substring(2), "\\\\");
            if (parts.isEmpty()) {
                return List.of();
            }
            int rootEnd = parts.size() >= 2 ? 2 : 1;
            String rootPath = "\\\\" + String.join("\\", parts.subList(0, rootEnd));
            segments.add(new PathSegment(rootPath, rootPath));
            for (int i = rootEnd; i < parts.size(); i++) {
                segments.add(new PathSegment(parts.get(i), "\\\\" + String.join("\\", parts.subList(0, i + 1))));
            }
            return segments;
        }

        if (windows) {
            // the path of another Windows machine may use either separator - "C:/work\src"
            String separator = pathStr.length() > 2 && pathStr.charAt(2) == '/' ? "/" : "\\";
            String[] parts = pathStr.split("[\\\\/]", -1);
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].isEmpty()) {
                    continue;
                }
                String displayName = i == 0 ? parts[i] + separator : parts[i];
                String fullPath = i == 0 ? parts[i] + separator : String.join(separator, List.of(parts).subList(0, i + 1));
                segments.add(new PathSegment(displayName, fullPath));
            }
            return segments;
        }

        String[] parts = pathStr.split("/", -1);
        if (parts.length > 0 && parts[0].isEmpty()) {
            segments.add(new PathSegment("/", "/"));
        }
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            String joined = String.join("/", List.of(parts).subList(0, i + 1));
            segments.add(new PathSegment(parts[i], joined.isEmpty() ? "/" : joined));
        }
        return segments;
    }

    private static List<String> nonEmptyParts(String value, String regex) {
        List<String> parts = new ArrayList<>();
        for (String part : value.split(regex, -1)) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }
}
