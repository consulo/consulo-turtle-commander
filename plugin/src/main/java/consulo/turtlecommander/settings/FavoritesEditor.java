package consulo.turtlecommander.settings;

import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserFactory;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.turtlecommander.service.FavoriteEntry;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FavoriteIcons;
import consulo.ui.Button;
import consulo.ui.ButtonStyle;
import consulo.ui.Component;
import consulo.ui.Space;
import consulo.ui.Table;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.util.io.FileUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The favourites list with add/remove, reordering, and per-entry colour and icon.
 *
 * <p>Favourites are stored per project, so this edits the first open project's list — which is the
 * one whose settings dialog the user is in.
 */
public class FavoritesEditor {
    private static final int PANEL_HEIGHT = 260;
    private static final int PATH_COLUMN_WIDTH = 500;

    private final MutableFlatDataModel<FavoriteEntry> myModel = FlatDataModel.of(List.of());
    private final Table<FavoriteEntry> myTable = Table.create(myModel);
    /** The chooser may answer off the UI thread; the model is only touched through here. */
    private final UIAccess myUIAccess;
    private final Component myRoot;

    @RequiredUIAccess
    public FavoritesEditor() {
        myUIAccess = UIAccess.current();

        myTable.setAllowMultipleSelect(false);
        myTable.setShowHeader(false);
        // the favourite's icon - colour tint or chosen glyph - beside its path; the frontends do not stretch a column
        // to the free width, so the path starts out wide
        myTable.addColumn(LocalizeValue.localizeTODO("Path"), entry -> entry)
            .setWidth(PATH_COLUMN_WIDTH)
            .setRenderer((presentation, item) -> {
                FavoriteEntry entry = item.getValue();
                if (entry != null) {
                    presentation.withIcon(FavoriteIcons.favoriteIcon(entry.color, entry.icon));
                    presentation.append(entry.path);
                }
            });

        loadFavorites();
        myRoot = buildPanel();
    }

    public Component getComponent() {
        return myRoot;
    }

    @RequiredUIAccess
    private Component buildPanel() {
        HorizontalLayout toolbar = HorizontalLayout.create(Space.X_SMALL);
        toolbar.add(toolbarButton(PlatformIconGroup.generalAdd(), "Add", this::addFavorite));
        toolbar.add(toolbarButton(PlatformIconGroup.generalRemove(), "Remove", this::removeSelected));
        toolbar.add(toolbarButton(PlatformIconGroup.actionsMoveup(), "Move Up", () -> move(-1)));
        toolbar.add(toolbarButton(PlatformIconGroup.actionsMovedown(), "Move Down", () -> move(1)));

        Button colorButton = Button.create(LocalizeValue.localizeTODO("Color..."));
        colorButton.addClickListener(event -> chooseColor(colorButton));
        toolbar.add(colorButton);
        Button iconButton = Button.create(LocalizeValue.localizeTODO("Icon..."));
        iconButton.addClickListener(event -> chooseIcon(iconButton));
        toolbar.add(iconButton);

        DockLayout panel = DockLayout.create(Space.X_SMALL).top(toolbar).center(ScrollableLayout.create(myTable));
        panel.setHeight(PANEL_HEIGHT);
        return LabeledLayout.create(LocalizeValue.localizeTODO("Favorites"), panel);
    }

    @RequiredUIAccess
    private static Button toolbarButton(Image icon, String tooltip, Runnable action) {
        Button button = Button.create(LocalizeValue.empty());
        button.setIcon(icon);
        button.addStyle(ButtonStyle.BORDERLESS);
        button.setToolTipText(LocalizeValue.localizeTODO(tooltip));
        button.addClickListener(event -> action.run());
        return button;
    }

    @RequiredUIAccess
    private void addFavorite() {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setTitle("Add Favorite");
        descriptor.setDescription("Select a directory to add to favorites");

        FileChooserFactory.getInstance().newBuilder(descriptor).project(currentProject()).showSingle().whenComplete((chosen, error) -> {
            if (chosen != null) {
                FavoriteEntry entry = new FavoriteEntry(FileUtil.toSystemIndependentName(chosen.toString()), "", "");
                myUIAccess.giveIfNeed(() -> {
                    myModel.add(entry);
                    myTable.select(entry);
                });
            }
        });
    }

    @RequiredUIAccess
    private void removeSelected() {
        FavoriteEntry selected = myTable.getSelectedItem();
        if (selected != null) {
            myModel.remove(selected);
        }
    }

    @RequiredUIAccess
    private void move(int offset) {
        FavoriteEntry selected = myTable.getSelectedItem();
        int index = selected == null ? -1 : myModel.indexOf(selected);
        int target = index + offset;
        if (index < 0 || target < 0 || target >= myModel.getSize()) {
            return;
        }
        myModel.remove(selected);
        myModel.add(selected, target);
        myTable.select(selected);
    }

    @RequiredUIAccess
    private void chooseColor(Component anchor) {
        FavoriteEntry entry = myTable.getSelectedItem();
        if (entry == null) {
            return;
        }
        UnifiedColorPicker.showColorPopup(anchor, "Choose Favorite Color", PresetColors.decode(entry.color), picked -> {
            // the entries are this editor's own copies, so they can change in place
            entry.color = PresetColors.toHex(picked);
            myModel.update(entry);
        });
    }

    @RequiredUIAccess
    private void chooseIcon(Component anchor) {
        FavoriteEntry entry = myTable.getSelectedItem();
        if (entry == null) {
            return;
        }
        UnifiedIconPicker.showIconChooserPopup(anchor, key -> {
            entry.icon = key;
            myModel.update(entry);
        });
    }

    public boolean isModified() {
        FileManagerStateService stateService = stateService();
        if (stateService == null) {
            return false;
        }
        List<FavoriteEntry> saved = stateService.getFavoriteEntries();
        if (myModel.getSize() != saved.size()) {
            return true;
        }
        for (int i = 0; i < myModel.getSize(); i++) {
            FavoriteEntry current = myModel.get(i);
            FavoriteEntry savedEntry = saved.get(i);
            if (!current.path.equals(savedEntry.path)
                || !current.color.equals(savedEntry.color)
                || !current.icon.equals(savedEntry.icon)) {
                return true;
            }
        }
        return false;
    }

    public void apply() {
        FileManagerStateService stateService = stateService();
        if (stateService == null) {
            return;
        }
        // copies again, so later edits here do not reach the stored list before the next apply
        List<FavoriteEntry> entries = new ArrayList<>(myModel.getSize());
        for (FavoriteEntry entry : myModel) {
            entries.add(new FavoriteEntry(entry.path, entry.color, entry.icon));
        }
        stateService.setFavoriteEntries(entries);
    }

    @RequiredUIAccess
    public void reset() {
        loadFavorites();
    }

    @RequiredUIAccess
    private void loadFavorites() {
        FileManagerStateService stateService = stateService();
        List<FavoriteEntry> entries = new ArrayList<>();
        if (stateService != null) {
            // Copied rather than shared, so cancelling the dialog leaves the stored list untouched.
            for (FavoriteEntry entry : stateService.getFavoriteEntries()) {
                entries.add(new FavoriteEntry(entry.path, entry.color, entry.icon));
            }
        }
        myModel.replaceAll(entries);
    }

    private static @Nullable FileManagerStateService stateService() {
        Project project = currentProject();
        return project != null ? FileManagerStateService.getInstance(project) : null;
    }

    private static @Nullable Project currentProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        return projects.length > 0 ? projects[0] : null;
    }
}
