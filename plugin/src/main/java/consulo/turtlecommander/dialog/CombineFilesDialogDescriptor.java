package consulo.turtlecommander.dialog;

import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.util.FileUtils;
import consulo.ui.Alerts;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.Space;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.DialogValue;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.VerticalLayout;
import org.jspecify.annotations.Nullable;

/** Target directory and file name for reassembling split parts. */
public final class CombineFilesDialogDescriptor extends DialogDescriptor {
    private final int myChunkCount;
    private final long myTotalSize;
    private final boolean myHasCrc;
    private final String myInitialTargetDirectory;
    private final String myInitialTargetFile;

    private @Nullable TextBox myTargetDirBox;
    private @Nullable TextBox myTargetFileBox;

    // read back in canHandle, so the getters answer the same after the dialog and its components are gone
    private String myTargetDirectory;
    private String myTargetFile;

    public CombineFilesDialogDescriptor(int chunkCount, long totalSize, boolean hasCrc, String targetDirectory,
                                        String targetFileName) {
        super(LocalizeValue.localizeTODO("Combine Files"));
        myChunkCount = chunkCount;
        myTotalSize = totalSize;
        myHasCrc = hasCrc;
        myInitialTargetDirectory = targetDirectory;
        myInitialTargetFile = targetFileName;
        myTargetDirectory = targetDirectory.trim();
        myTargetFile = targetFileName.trim();
    }

    public String getTargetDirectory() {
        return myTargetDirectory;
    }

    public String getTargetFile() {
        return myTargetFile;
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        VerticalLayout layout = VerticalLayout.create(Space.SMALL);

        String info = myChunkCount + " parts, " + FileUtils.formatSize(myTotalSize) + " total"
            + (myHasCrc ? " (CRC will be verified)" : "");
        layout.add(Label.create(LocalizeValue.of(info)));

        TextBox targetDirBox = TextBox.create(myInitialTargetDirectory);
        myTargetDirBox = targetDirBox;
        layout.add(Label.create(LocalizeValue.localizeTODO("Target directory:")));
        // a dock center stretches the box over the width of the dialog; added as is, it kept its default width
        layout.add(DockLayout.create().center(targetDirBox));

        TextBox targetFileBox = TextBox.create(myInitialTargetFile);
        myTargetFileBox = targetFileBox;
        layout.add(Label.create(LocalizeValue.localizeTODO("Target file name:")));
        layout.add(DockLayout.create().center(targetFileBox));

        return layout;
    }

    @Override
    public Component getPreferredFocusedComponent() {
        TextBox targetDirBox = myTargetDirBox;
        return targetDirBox != null ? targetDirBox : super.getPreferredFocusedComponent();
    }

    @Override
    protected DialogOkAction createOkAction() {
        return new DialogOkAction(LocalizeValue.localizeTODO("Combine"));
    }

    /** A blank directory or file name keeps the dialog open, with the reason. */
    @RequiredUIAccess
    @Override
    public boolean canHandle(AnAction action, @Nullable DialogValue value) {
        if (!(action instanceof DialogOkAction)) {
            return true;
        }
        TextBox targetDirBox = myTargetDirBox;
        String targetDirectory = targetDirBox != null ? targetDirBox.getValueOrError().trim() : myTargetDirectory;
        if (targetDirectory.isEmpty()) {
            Alerts.okError(LocalizeValue.localizeTODO("Target directory cannot be empty.")).showAsync();
            return false;
        }
        TextBox targetFileBox = myTargetFileBox;
        String targetFile = targetFileBox != null ? targetFileBox.getValueOrError().trim() : myTargetFile;
        if (targetFile.isEmpty()) {
            Alerts.okError(LocalizeValue.localizeTODO("Target file name cannot be empty.")).showAsync();
            return false;
        }
        myTargetDirectory = targetDirectory;
        myTargetFile = targetFile;
        return true;
    }
}
