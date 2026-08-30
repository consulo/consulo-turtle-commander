package consulo.turtlecommander.action;

import consulo.application.progress.ProgressIndicator;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.util.BackgroundProgress;
import consulo.turtlecommander.util.FileUtils;
import consulo.turtlecommander.util.UiTask;
import consulo.ui.ex.CopyPasteManager;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.Messages;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Copies a checksum of the right-clicked file. Hashing streams the whole file, so it runs under a
 * cancellable background progress and only the clipboard write returns to the UI thread.
 */
public abstract class CopyHashAction extends EdtAction {
    /** Computes the hash, reporting progress and honouring cancellation through the indicator. */
    @FunctionalInterface
    public interface HashFunction {
        String compute(Path path, ProgressIndicator indicator) throws Exception;
    }

    private final String myLabel;
    private final HashFunction myCompute;

    protected CopyHashAction(String label, HashFunction compute) {
        super(LocalizeValue.localizeTODO(label));
        myLabel = label;
        myCompute = compute;
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(hashableEntry(e) != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileEntry entry = hashableEntry(e);
        Project project = e.getData(Project.KEY);
        if (entry == null || project == null) {
            return;
        }

        BackgroundProgress.run(project,
            LocalizeValue.localizeTODO("Computing " + myLabel + " of " + entry.name()), indicator -> {
                String result;
                try {
                    result = myCompute.compute(entry.path(), indicator);
                }
                catch (Exception ex) {
                    if (!indicator.isCanceled()) {
                        UiTask.ui(() -> Messages.showErrorDialog(project,
                            "Failed to compute " + myLabel + ": " + FileUtils.fileErrorMessage(ex),
                            "Copy " + myLabel));
                    }
                    return;
                }
                UiTask.ui(() -> CopyPasteManager.getInstance().setText(result));
            });
    }

    /** Only a real file can be hashed — not a directory, and not the parent link. */
    private static @Nullable FileEntry hashableEntry(AnActionEvent e) {
        FileEntry entry = FileContextMenuState.getClickedEntry(e);
        return entry != null && !entry.parentLink() && !entry.directory() ? entry : null;
    }
}
