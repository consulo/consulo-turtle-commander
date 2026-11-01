package consulo.turtlecommander.dialog;

import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.service.OverwritePolicy;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Destination and overwrite policy for extracting one or more archives. The destination is handed back as typed;
 * whether it is usable is found out by the extraction itself, off the UI thread.
 */
public final class ExtractDialogDescriptor extends DialogDescriptor {
    /** Names listed before the list scrolls. */
    private static final int MAX_LISTED_ROWS = 10;

    private final List<FileEntry> mySources;
    private final String myDefaultDestination;

    private @Nullable TextBox myDestinationBox;
    private @Nullable ComboBox<OverwritePolicy> myPolicyBox;

    public ExtractDialogDescriptor(List<FileEntry> sources, String defaultDestination) {
        super(LocalizeValue.localizeTODO("Extract Files"));
        mySources = sources;
        myDefaultDestination = defaultDestination;
    }

    public String getDestinationPath() {
        TextBox destinationBox = myDestinationBox;
        return destinationBox != null ? destinationBox.getValueOrError().trim() : myDefaultDestination.trim();
    }

    public OverwritePolicy getPolicy() {
        ComboBox<OverwritePolicy> policyBox = myPolicyBox;
        OverwritePolicy policy = policyBox != null ? policyBox.getValue() : null;
        return policy != null ? policy : OverwritePolicy.ASK;
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        VerticalLayout layout = VerticalLayout.create(Space.SMALL);

        String prompt = mySources.size() == 1
            ? "Extract \"" + mySources.get(0).name() + "\" to:"
            : "Extract " + mySources.size() + " archives to:";
        layout.add(Label.create(LocalizeValue.of(prompt)));

        if (mySources.size() > 1) {
            List<String> names = new ArrayList<>(mySources.size());
            for (FileEntry entry : mySources) {
                names.add(entry.name());
            }
            layout.add(ScrollableLayout.create(ListBox.create(names)));
        }

        TextBox destinationBox = TextBox.create(myDefaultDestination);
        myDestinationBox = destinationBox;
        // a dock center stretches the box over the width of the dialog; added as is, it kept its default width
        layout.add(DockLayout.create().center(destinationBox));

        ComboBox<OverwritePolicy> policyBox = ComboBox.create(OverwritePolicy.values());
        policyBox.setTextRenderer(policy -> LocalizeValue.of(OverwritePolicyRenderer.label(policy)));
        policyBox.setValue(TurtleCommanderSettings.getInstance().getDefaultOverwritePolicy());
        myPolicyBox = policyBox;

        DockLayout policyRow = DockLayout.create();
        policyRow.left(Label.create(LocalizeValue.localizeTODO("If a target file already exists:")));
        policyRow.right(policyBox);
        layout.add(policyRow);

        return layout;
    }

    @Override
    public Component getPreferredFocusedComponent() {
        TextBox destinationBox = myDestinationBox;
        return destinationBox != null ? destinationBox : super.getPreferredFocusedComponent();
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
        return new DialogOkAction(LocalizeValue.localizeTODO("Extract"));
    }
}
