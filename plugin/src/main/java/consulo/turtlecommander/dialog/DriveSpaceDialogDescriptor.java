package consulo.turtlecommander.dialog;

import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.model.DriveInfo;
import consulo.turtlecommander.util.FileUtils;
import consulo.ui.AdvancedLabel;
import consulo.ui.Component;
import consulo.ui.ProgressBar;
import consulo.ui.ProgressBarStyle;
import consulo.ui.Space;
import consulo.ui.TextAttribute;
import consulo.ui.WidthAndHeight;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.VerticalLayout;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** How full every volume is: a line of numbers and a bar of the used share per drive. */
public final class DriveSpaceDialogDescriptor extends DialogDescriptor {
    /** Rows shown before the list scrolls. */
    private static final int MAX_VISIBLE_ROWS = 6;

    /** How full a drive is before its bar warns, and before it reads as an error. */
    private static final int WARNING_PERCENT = 75;
    private static final int ERROR_PERCENT = 90;

    private final List<DriveInfo> myDrives;

    public DriveSpaceDialogDescriptor(List<DriveInfo> drives) {
        super(LocalizeValue.localizeTODO("Drive Space"));
        myDrives = drives;
    }

    @RequiredUIAccess
    public static void show(Project project, List<DriveInfo> drives) {
        project.getApplication().getInstance(DialogService.class).build(new DriveSpaceDialogDescriptor(drives)).showAsync();
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        VerticalLayout rows = VerticalLayout.create(Space.LARGE);
        for (DriveInfo drive : myDrives) {
            rows.add(createRow(drive));
        }
        return ScrollableLayout.create(rows);
    }

    @RequiredUIAccess
    private static Component createRow(DriveInfo drive) {
        long percentUsed = drive.totalSpace() > 0 ? drive.usedSpace() * 100 / drive.totalSpace() : 0;
        String text = drive.label() + "  —  " + FileUtils.formatSize(drive.freeSpace()) + " free of "
            + FileUtils.formatSize(drive.totalSpace()) + "  (" + percentUsed + "% used)";

        AdvancedLabel label = AdvancedLabel.create();
        label.updatePresentation(presentation -> presentation.append(text, TextAttribute.REGULAR_BOLD));

        ProgressBar bar = ProgressBar.create();
        bar.setMinimum(0);
        bar.setMaximum(100);
        bar.setValue((int) percentUsed);
        bar.addStyle(ProgressBarStyle.HEIGHT_MEDIUM);
        // a nearly full drive is what the dialog is opened to find
        if (percentUsed >= ERROR_PERCENT) {
            bar.addStyle(ProgressBarStyle.ERROR);
        }
        else if (percentUsed >= WARNING_PERCENT) {
            bar.addStyle(ProgressBarStyle.WARNING);
        }

        VerticalLayout row = VerticalLayout.create(Space.X_SMALL);
        row.add(label);
        row.add(bar);
        return row;
    }

    @Override
    public @Nullable WidthAndHeight getInitialSize() {
        int visibleRows = Math.min(Math.max(myDrives.size(), 1), MAX_VISIBLE_ROWS);
        return WidthAndHeight.ofFont(60, visibleRows * 3.5f + 2);
    }

    /** Nothing to confirm - the one button only closes. */
    @Override
    public AnAction[] createActions(boolean inverseOrder) {
        return new AnAction[]{new DialogOkAction(LocalizeValue.localizeTODO("Close"))};
    }
}
