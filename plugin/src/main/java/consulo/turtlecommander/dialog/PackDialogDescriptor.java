package consulo.turtlecommander.dialog;

import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.operation.ArchiveFormat;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.ListBox;
import consulo.ui.Space;
import consulo.ui.TextBox;
import consulo.ui.WidthAndHeight;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Destination archive, format and post-pack cleanup for a Pack operation. */
public final class PackDialogDescriptor extends DialogDescriptor {
    /** Names listed before the list scrolls. */
    private static final int MAX_LISTED_ROWS = 10;

    private final List<FileEntry> mySources;
    private final String myDefaultArchivePath;

    private @Nullable TextBox myArchiveBox;
    private @Nullable ComboBox<ArchiveFormat> myFormatBox;
    private @Nullable CheckBox myDeleteCheckBox;

    public PackDialogDescriptor(List<FileEntry> sources, String defaultArchivePath) {
        super(LocalizeValue.localizeTODO("Pack Files"));
        mySources = sources;
        myDefaultArchivePath = defaultArchivePath;
    }

    public String getArchivePath() {
        TextBox archiveBox = myArchiveBox;
        String path = archiveBox != null ? archiveBox.getValueOrError().trim() : myDefaultArchivePath.trim();
        String swapped = withExtensionOf(path, getArchiveFormat());
        return swapped != null ? swapped : path;
    }

    public ArchiveFormat getArchiveFormat() {
        ComboBox<ArchiveFormat> formatBox = myFormatBox;
        ArchiveFormat format = formatBox != null ? formatBox.getValue() : null;
        return format != null ? format : ArchiveFormat.ZIP;
    }

    public boolean isDeleteAfterPacking() {
        CheckBox deleteCheckBox = myDeleteCheckBox;
        return deleteCheckBox != null && deleteCheckBox.getValueOrError();
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        VerticalLayout layout = VerticalLayout.create(Space.SMALL);

        String prompt = mySources.size() == 1
            ? "Pack \"" + mySources.get(0).name() + "\" to archive:"
            : "Pack " + mySources.size() + " items to archive:";
        layout.add(Label.create(LocalizeValue.of(prompt)));

        if (mySources.size() > 1) {
            List<String> names = new ArrayList<>(mySources.size());
            for (FileEntry entry : mySources) {
                names.add(entry.name());
            }
            layout.add(ScrollableLayout.create(ListBox.create(names)));
        }

        TextBox archiveBox = TextBox.create(myDefaultArchivePath);
        myArchiveBox = archiveBox;

        ComboBox<ArchiveFormat> formatBox = ComboBox.create(ArchiveFormat.values());
        formatBox.setTextRenderer(format -> format == null ? LocalizeValue.empty() : LocalizeValue.of(format.label()));
        formatBox.setValue(ArchiveFormat.ZIP);
        myFormatBox = formatBox;
        // registered after the initial value, so opening the dialog never rewrites the suggested name
        formatBox.addValueListener(event -> {
            ArchiveFormat format = event.getValue();
            String swapped = format != null ? withExtensionOf(archiveBox.getValueOrError().trim(), format) : null;
            if (swapped != null) {
                archiveBox.setValue(swapped);
            }
        });

        // a dock center stretches the box over the width of the dialog, leaving the format at its own width
        layout.add(DockLayout.create().center(archiveBox).right(formatBox));

        CheckBox deleteCheckBox = CheckBox.create(LocalizeValue.localizeTODO("Delete files after packing"));
        myDeleteCheckBox = deleteCheckBox;
        layout.add(deleteCheckBox);

        return layout;
    }

    @Override
    public Component getPreferredFocusedComponent() {
        TextBox archiveBox = myArchiveBox;
        return archiveBox != null ? archiveBox : super.getPreferredFocusedComponent();
    }

    @Override
    public @Nullable WidthAndHeight getInitialSize() {
        if (mySources.size() <= 1) {
            return null;
        }
        // in font sizes: a row of text is about two, the rest of the dialog about fourteen
        return WidthAndHeight.ofFont(45, 14 + Math.min(mySources.size(), MAX_LISTED_ROWS) * 2);
    }

    @Override
    protected DialogOkAction createOkAction() {
        return new DialogOkAction(LocalizeValue.localizeTODO("Pack"));
    }

    /**
     * Swaps a recognized archive extension for the selected format's. Returns {@code null} when the
     * path carries none, so a name the user typed by hand is left alone.
     */
    private static @Nullable String withExtensionOf(String path, ArchiveFormat format) {
        for (ArchiveFormat candidate : ArchiveFormat.values()) {
            if (path.endsWith(candidate.extension())) {
                return StringUtil.trimEnd(path, candidate.extension()) + format.extension();
            }
        }
        return null;
    }
}
