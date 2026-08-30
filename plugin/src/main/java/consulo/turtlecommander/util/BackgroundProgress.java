package consulo.turtlecommander.util;

import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.Task;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.vfs.VfsOpenProgress;

import java.util.function.Consumer;

/**
 * Runs a long operation under a cancellable background progress, handing it a live
 * {@link ProgressIndicator} for text / text2 / fraction updates.
 *
 * <p>Replaces the Kotlin coroutine bridge, which composed {@code withBackgroundProgress},
 * {@code coroutineToIndicator} and {@code runBlockingCancellable} to get a thread-bound indicator
 * out of a suspending context. Consulo's {@link Task.Backgroundable} already hands the body a real
 * indicator on a background thread, so none of that plumbing is needed.
 *
 * <p>The call returns as soon as the task is queued; the body runs on a background thread. Cancel —
 * whether by the user or by project close — surfaces through
 * {@link ProgressIndicator#isCanceled()}, which long loops must poll. Operations that need to
 * finish an in-flight file and still run cleanup should poll rather than assume they are killed at
 * an arbitrary point.
 */
public final class BackgroundProgress {
    private BackgroundProgress() {
    }

    public static void run(Project project, LocalizeValue title, Consumer<ProgressIndicator> body) {
        Task.Backgroundable.queue(project, title, true, body);
    }

    /** Adapts the task's indicator to the archive-open progress hook. */
    public static VfsOpenProgress asVfsOpenProgress(ProgressIndicator indicator) {
        return VfsOpenProgress.fromIndicator(indicator);
    }
}
