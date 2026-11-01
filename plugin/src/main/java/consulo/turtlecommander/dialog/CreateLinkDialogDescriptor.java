package consulo.turtlecommander.dialog;

import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.util.UiTask;
import consulo.ui.Alerts;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.ListBox;
import consulo.ui.RadioButton;
import consulo.ui.RadioGroup;
import consulo.ui.Space;
import consulo.ui.TextBox;
import consulo.ui.UIAccess;
import consulo.ui.WidthAndHeight;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.dialog.Dialog;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.ex.dialog.DialogValue;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.VerticalLayout;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

/**
 * Dual-pane "create link" dialog: the active panel's sources become links in the opposite panel's destination
 * directory. For a single source the link path (location + name) is editable so the link can be renamed; for several,
 * the field is the location directory and each link keeps its source's name. Symbolic links store an absolute target
 * or one relative to the link's own directory (relative keeps the link portable when source and destination share a
 * root); hard links always reference the existing file directly, so that choice does not apply to them.
 */
public final class CreateLinkDialogDescriptor extends DialogDescriptor {
    /** Names listed before the list scrolls. */
    private static final int MAX_LISTED_ROWS = 8;

    public record LinkSpec(Path link, Path target, boolean hard) {
    }

    private final List<FileEntry> mySources;
    private final Path myDestination;
    private final String myDestinationDisplayPath;
    private final boolean mySingle;
    private final boolean mySameRoot;

    private @Nullable TextBox myLinkBox;
    private @Nullable RadioGroup<Boolean> myHardGroup;
    private @Nullable RadioGroup<Boolean> myRelativeGroup;

    private @Nullable Dialog myDialog;
    private @Nullable UIAccess myUIAccess;
    private boolean myChecking;
    private boolean myClosed;

    private List<LinkSpec> mySpecs = List.of();

    public CreateLinkDialogDescriptor(List<FileEntry> sources, Path destination) {
        this(sources, destination, destination.toString());
    }

    public CreateLinkDialogDescriptor(List<FileEntry> sources, Path destination, String destinationDisplayPath) {
        super(LocalizeValue.localizeTODO("Create Link"));
        mySources = sources;
        myDestination = destination;
        myDestinationDisplayPath = destinationDisplayPath;
        mySingle = sources.size() == 1;
        // relativize across roots (different drives) is impossible, so relative is only the default on one root
        mySameRoot = sources.stream().allMatch(entry -> Objects.equals(entry.path().getRoot(), destination.getRoot()));
    }

    /**
     * Shows the dialog. The descriptor builds it itself: OK only closes it once the link paths have been checked off
     * the UI thread, and closing it from there needs the dialog.
     */
    @RequiredUIAccess
    public CompletableFuture<DialogValue> showAsync() {
        Dialog dialog = Application.get().getInstance(DialogService.class).build(this);
        myDialog = dialog;
        return dialog.showAsync();
    }

    /** The links to create - read once the dialog closed with OK. */
    public List<LinkSpec> getSpecs() {
        return mySpecs;
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        myUIAccess = UIAccess.current();
        // a check still running when the dialog goes away must not close it a second time
        Disposer.register(uiDisposable, () -> myClosed = true);

        VerticalLayout layout = VerticalLayout.create(Space.SMALL);

        String sourceLabel = mySingle
            ? "Link to \"" + mySources.get(0).name() + "\":"
            : "Link to " + mySources.size() + " items in:";
        layout.add(Label.create(LocalizeValue.of(sourceLabel)));

        if (!mySingle) {
            List<String> names = new ArrayList<>(mySources.size());
            for (FileEntry entry : mySources) {
                names.add(entry.name());
            }
            layout.add(ScrollableLayout.create(ListBox.create(names)));
        }

        layout.add(Label.create(LocalizeValue.localizeTODO(mySingle ? "Link path:" : "Location:")));
        TextBox linkBox = TextBox.create(mySingle
            ? myDestination.resolve(mySources.get(0).name()).toString()
            : myDestinationDisplayPath);
        myLinkBox = linkBox;
        // a dock center stretches the box over the width of the dialog
        layout.add(DockLayout.create().center(linkBox));

        RadioGroup<Boolean> hardGroup = RadioGroup.create();
        RadioButton symbolicButton = hardGroup.newButton(LocalizeValue.localizeTODO("Symbolic link"), false);
        RadioButton hardButton = hardGroup.newButton(LocalizeValue.localizeTODO("Hard link"), true);
        hardGroup.setValue(false);
        myHardGroup = hardGroup;
        layout.add(radioRow("Link type:", symbolicButton, hardButton));

        RadioGroup<Boolean> relativeGroup = RadioGroup.create();
        RadioButton absoluteButton = relativeGroup.newButton(LocalizeValue.localizeTODO("Absolute path"), false);
        RadioButton relativeButton = relativeGroup.newButton(LocalizeValue.localizeTODO("Relative to link"), true);
        relativeGroup.setValue(mySameRoot);
        myRelativeGroup = relativeGroup;
        layout.add(radioRow("Points to:", absoluteButton, relativeButton));

        // a hard link always points at the existing file's data, so absolute or relative means nothing for it
        hardGroup.addValueListener(hard -> {
            boolean symbolic = !Boolean.TRUE.equals(hard);
            absoluteButton.setEnabled(symbolic);
            relativeButton.setEnabled(symbolic);
        });
        return layout;
    }

    @RequiredUIAccess
    private static Component radioRow(String label, RadioButton first, RadioButton second) {
        HorizontalLayout row = HorizontalLayout.create(Space.MEDIUM);
        row.add(Label.create(LocalizeValue.localizeTODO(label)));
        row.add(first);
        row.add(second);
        return row;
    }

    @Override
    public Component getPreferredFocusedComponent() {
        TextBox linkBox = myLinkBox;
        return linkBox != null ? linkBox : super.getPreferredFocusedComponent();
    }

    /** The labels, the box and the radio rows take what they need; only the list of names is given a height. */
    @Override
    public @Nullable WidthAndHeight getInitialSize() {
        if (mySingle) {
            return null;
        }
        // in font sizes: a row of text is about two, the rest of the dialog about eighteen
        return WidthAndHeight.ofFont(45, 18 + Math.min(mySources.size(), MAX_LISTED_ROWS) * 2);
    }

    @Override
    protected DialogOkAction createOkAction() {
        return new DialogOkAction(LocalizeValue.localizeTODO("Create"));
    }

    /**
     * Whatever can be told from the typed path and the entries keeps the dialog open at once. Whether something
     * already sits at a link path needs a stat, which on a dead network destination blocks for the SMB timeout - that
     * runs off the UI thread, and the dialog closes itself once it passes.
     */
    @RequiredUIAccess
    @Override
    public boolean canHandle(AnAction action, @Nullable DialogValue value) {
        if (!(action instanceof DialogOkAction)) {
            return true;
        }
        if (myChecking) {
            return false;
        }
        List<LinkSpec> computed = computeSpecs();
        if (computed == null) {
            return false;
        }
        Dialog dialog = myDialog;
        UIAccess uiAccess = myUIAccess;
        if (dialog == null || uiAccess == null) {
            // built without showAsync() - nothing could close it later, so skip the check rather than never close
            mySpecs = computed;
            return true;
        }

        myChecking = true;
        UiTask.background(() -> {
            LinkSpec clash = firstExisting(computed);
            uiAccess.give(() -> {
                myChecking = false;
                if (myClosed) {
                    return;
                }
                if (clash != null) {
                    showError("Already exists:\n" + clash.link());
                    return;
                }
                mySpecs = computed;
                dialog.doOkAction(getOkValue());
            });
        });
        return false;
    }

    /** Null when the input is unusable; the reason has been shown. */
    @RequiredUIAccess
    private @Nullable List<LinkSpec> computeSpecs() {
        TextBox linkBox = myLinkBox;
        String text = linkBox != null ? linkBox.getValueOrError().trim() : "";
        if (text.isEmpty()) {
            showError("Link path cannot be empty.");
            return null;
        }

        Path entered;
        try {
            entered = Path.of(text);
        }
        catch (InvalidPathException e) {
            showError("Invalid path: " + e.getMessage());
            return null;
        }
        Path resolved = entered.isAbsolute() ? entered.normalize() : myDestination.resolve(entered).normalize();

        RadioGroup<Boolean> hardGroup = myHardGroup;
        boolean hard = hardGroup != null && Boolean.TRUE.equals(hardGroup.getValue());
        // hard links cannot reference directories - rejected up front rather than failing per item
        if (hard) {
            StringJoiner directories = new StringJoiner("\n");
            for (FileEntry entry : mySources) {
                if (entry.directory()) {
                    directories.add(entry.name());
                }
            }
            if (directories.length() > 0) {
                showError("Hard links cannot point to directories:\n" + directories);
                return null;
            }
        }

        RadioGroup<Boolean> relativeGroup = myRelativeGroup;
        boolean relative = !hard && (relativeGroup != null ? Boolean.TRUE.equals(relativeGroup.getValue()) : mySameRoot);

        List<LinkSpec> computed = new ArrayList<>();
        if (mySingle) {
            if (resolved.getFileName() == null) {
                showError("Link path must include a name.");
                return null;
            }
            Path linkDir = resolved.getParent() != null ? resolved.getParent() : myDestination;
            computed.add(new LinkSpec(resolved, targetFor(mySources.get(0).path(), linkDir, relative), hard));
        }
        else {
            for (FileEntry entry : mySources) {
                computed.add(new LinkSpec(resolved.resolve(entry.name()), targetFor(entry.path(), resolved, relative), hard));
            }
        }
        return computed;
    }

    /** NOFOLLOW so that a link already at the path counts, dangling or not. */
    private static @Nullable LinkSpec firstExisting(List<LinkSpec> specs) {
        for (LinkSpec spec : specs) {
            if (Files.exists(spec.link(), LinkOption.NOFOLLOW_LINKS)) {
                return spec;
            }
        }
        return null;
    }

    /** Absolute unless a relative symbolic link was asked for and the two paths can be relativized. */
    private static Path targetFor(Path source, Path linkDir, boolean relative) {
        Path absolute = source.toAbsolutePath().normalize();
        if (!relative) {
            return absolute;
        }
        try {
            return linkDir.toAbsolutePath().normalize().relativize(absolute);
        }
        catch (IllegalArgumentException e) {
            return absolute;
        }
    }

    @RequiredUIAccess
    private static void showError(String message) {
        Alerts.okError(LocalizeValue.of(message)).showAsync();
    }
}
