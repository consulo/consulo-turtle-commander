package consulo.turtlecommander.util;

import consulo.application.Application;
import consulo.logging.Logger;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The "do the I/O off the UI thread, then update the UI" pattern the panels use everywhere.
 *
 * <p>Kotlin wrote this as {@code scope.launch { withContext(IO) { … }; withContext(EDT) { … } }}.
 * The equivalent here is a pooled thread plus {@code invokeLater}, which is all those coroutines
 * were doing: none of the call sites awaited a result or needed structured concurrency.
 *
 * <p>Every method returns immediately. Background failures are logged rather than thrown, because
 * these run detached from any caller that could handle them — a failed directory listing must not
 * take down the UI thread.
 */
public final class UiTask {
    private static final Logger LOG = Logger.getInstance(UiTask.class);

    private UiTask() {
    }

    /** Runs {@code work} on a pooled thread. */
    public static void background(Runnable work) {
        Application.get().executeOnPooledThread(() -> {
            try {
                work.run();
            }
            catch (Exception e) {
                LOG.warn("Background task failed", e);
            }
        });
    }

    /** Runs {@code work} on the UI thread. Safe to call from anywhere. */
    public static void ui(Runnable work) {
        Application.get().invokeLater(work);
    }

    /**
     * Runs {@code work} on the UI thread and blocks until it finishes. Needed for the modal prompts
     * a background operation asks mid-flight: an overwrite confirmation is worthless if the copy
     * has already moved past that file by the time the user answers.
     *
     * <p>Must not be called from the UI thread — it would wait on itself.
     */
    public static void uiAndWait(Runnable work) {
        Application.get().invokeAndWait(work);
    }

    /**
     * Computes {@code work} on a pooled thread and hands the result to {@code onUi} on the UI
     * thread. {@code onUi} is skipped entirely when the background step fails, so consumers never
     * see a half-computed value.
     */
    public static <T> void backgroundThenUi(Supplier<T> work, Consumer<T> onUi) {
        Application.get().executeOnPooledThread(() -> {
            T result;
            try {
                result = work.get();
            }
            catch (Exception e) {
                LOG.warn("Background task failed", e);
                return;
            }
            Application.get().invokeLater(() -> onUi.accept(result));
        });
    }
}
