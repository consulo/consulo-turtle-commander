package consulo.turtlecommander.dialog;

import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.ListBox;
import consulo.ui.Space;
import consulo.ui.WidthAndHeight;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.VerticalLayout;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Asks before deleting: what goes, whether to the recycle bin or for good, and - for more than one entry - the names.
 */
public final class DeleteDialogDescriptor extends DialogDescriptor {
    /** Names listed before the list scrolls. */
    private static final int MAX_LISTED_ROWS = 10;

    private final List<FileEntry> mySources;
    private final boolean myUseRecycleBin;
    private final @Nullable String myNote;
    private final @Nullable String myCheckboxText;

    private @Nullable CheckBox myCheckbox;

    /**
     * @param note         optional explanation under the prompt, e.g. why a recycle-bin delete became permanent
     * @param checkboxText optional opt-out checkbox; read the result via {@link #isCheckboxSelected}
     */
    public DeleteDialogDescriptor(List<FileEntry> sources, boolean useRecycleBin, @Nullable String note,
                                  @Nullable String checkboxText) {
        super(LocalizeValue.localizeTODO(useRecycleBin ? "Move to Recycle Bin" : "Delete"));
        mySources = sources;
        myUseRecycleBin = useRecycleBin;
        myNote = note;
        myCheckboxText = checkboxText;
    }

    /** False when no checkbox was requested. */
    public boolean isCheckboxSelected() {
        CheckBox checkbox = myCheckbox;
        return checkbox != null && checkbox.getValueOrError();
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        VerticalLayout layout = VerticalLayout.create(Space.SMALL);
        layout.add(Label.create(LocalizeValue.of(buildPrompt(mySources, myUseRecycleBin))));

        if (myNote != null) {
            layout.add(Label.create(LocalizeValue.of(myNote)));
        }

        if (myCheckboxText != null) {
            CheckBox checkbox = CheckBox.create(LocalizeValue.of(myCheckboxText));
            myCheckbox = checkbox;
            layout.add(checkbox);
        }

        if (mySources.size() > 1) {
            List<String> names = new ArrayList<>(mySources.size());
            for (FileEntry entry : mySources) {
                names.add(entry.name());
            }
            layout.add(ScrollableLayout.create(ListBox.create(names)));
        }
        return layout;
    }

    /** The prompt, the note and the checkbox take what they need; only the list of names is given a height. */
    @Override
    public @Nullable WidthAndHeight getInitialSize() {
        if (mySources.size() <= 1) {
            return null;
        }
        int extraRows = (myNote != null ? 1 : 0) + (myCheckboxText != null ? 1 : 0);
        int listedRows = Math.min(mySources.size(), MAX_LISTED_ROWS);
        // in font sizes: a row of text is about two, the prompt and the buttons about eight
        return WidthAndHeight.ofFont(40, 10 + (extraRows + listedRows) * 2);
    }

    @Override
    protected DialogOkAction createOkAction() {
        return new DialogOkAction(LocalizeValue.localizeTODO(myUseRecycleBin ? "Move to Recycle Bin" : "Delete"));
    }

    /**
     * The wording depends on whether the deletion is permanent or reversible, so tests assert against this without
     * showing the dialog.
     */
    public static String buildPrompt(List<FileEntry> sources, boolean useRecycleBin) {
        if (sources.size() == 1) {
            String name = sources.get(0).name();
            return useRecycleBin
                ? "Move \"" + name + "\" to Recycle Bin?"
                : "Delete \"" + name + "\"?";
        }
        return useRecycleBin
            ? "Move " + sources.size() + " items to Recycle Bin?"
            : "Delete " + sources.size() + " items?";
    }
}
