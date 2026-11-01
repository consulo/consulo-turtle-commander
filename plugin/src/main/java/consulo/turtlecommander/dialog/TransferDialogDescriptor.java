package consulo.turtlecommander.dialog;

import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.service.OverwritePolicy;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.ui.Alerts;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.ListBox;
import consulo.ui.Space;
import consulo.ui.TextBox;
import consulo.ui.WidthAndHeight;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.DialogValue;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.VerticalLayout;
import org.jspecify.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Asks where a copy or a move goes and what to do with the files already there. The typed destination is only parsed
 * here; whether it is a usable directory is found out by the transfer itself, off the UI thread - a dead network
 * destination can block a stat for the whole SMB timeout.
 */
public final class TransferDialogDescriptor extends DialogDescriptor {
    /** Names listed before the list scrolls. */
    private static final int MAX_LISTED_ROWS = 10;

    private final String myVerb;
    private final List<FileEntry> mySources;
    private final Path myDestination;
    private final String myDestinationDisplayPath;

    private @Nullable TextBox myDestinationBox;
    private @Nullable ComboBox<OverwritePolicy> myPolicyBox;

    private Path myTargetDirectory;

    public TransferDialogDescriptor(String verb, List<FileEntry> sources, Path destination, String destinationDisplayPath) {
        super(LocalizeValue.of(verb));
        myVerb = verb;
        mySources = sources;
        myDestination = destination;
        myDestinationDisplayPath = destinationDisplayPath;
        myTargetDirectory = destination;
    }

    /** The destination as the user left it - read once the dialog closed with OK. */
    public Path getTargetDirectory() {
        return myTargetDirectory;
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
            ? myVerb + " \"" + mySources.get(0).name() + "\" to:"
            : myVerb + " " + mySources.size() + " items to:";
        layout.add(Label.create(LocalizeValue.of(prompt)));

        if (mySources.size() > 1) {
            List<String> names = new ArrayList<>(mySources.size());
            for (FileEntry entry : mySources) {
                names.add(entry.name());
            }
            layout.add(ScrollableLayout.create(ListBox.create(names)));
        }

        TextBox destinationBox = TextBox.create(myDestinationDisplayPath);
        myDestinationBox = destinationBox;
        // a dock center stretches the box over the width of the dialog; added as is, it kept its default width
        layout.add(DockLayout.create().center(destinationBox));

        ComboBox<OverwritePolicy> policyBox = ComboBox.create(OverwritePolicy.values());
        policyBox.setTextRenderer(policy -> LocalizeValue.of(OverwritePolicyRenderer.label(policy)));
        // the saved default; ASK on a fresh install, so overwriting without asking has to be opted into
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
        return new DialogOkAction(LocalizeValue.of(myVerb));
    }

    /** A blank or unparsable destination keeps the dialog open, with the reason. */
    @RequiredUIAccess
    @Override
    public boolean canHandle(AnAction action, @Nullable DialogValue value) {
        if (!(action instanceof DialogOkAction)) {
            return true;
        }
        TextBox destinationBox = myDestinationBox;
        String text = destinationBox != null ? destinationBox.getValueOrError().trim() : myDestinationDisplayPath;
        if (text.isEmpty()) {
            Alerts.okError(LocalizeValue.localizeTODO("Destination cannot be empty.")).showAsync();
            return false;
        }
        if (text.equals(myDestinationDisplayPath)) {
            myTargetDirectory = myDestination;
            return true;
        }
        try {
            Path parsed = Path.of(text);
            myTargetDirectory = parsed.isAbsolute() ? parsed.normalize() : myDestination.resolve(parsed).normalize();
            return true;
        }
        catch (InvalidPathException e) {
            Alerts.okError(LocalizeValue.of("Invalid destination path: " + e.getMessage())).showAsync();
            return false;
        }
    }
}
