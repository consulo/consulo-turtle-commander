package consulo.turtlecommander.vfs;

import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;

/**
 * Progress/cancellation hook for the extract-at-open phase of temp-dir VFS implementations.
 * Passed explicitly by progress-aware call sites rather than relying on the thread-bound global
 * indicator, which isn't available when extraction runs on a pooled background thread.
 */
public interface VfsOpenProgress {
    VfsOpenProgress NOOP = new VfsOpenProgress() {
        @Override
        public void onEntry(int index, int total, String name) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    /** Reports entry {@code index} of {@code total} ({@code total <= 0} when unknown). */
    void onEntry(int index, int total, String name);

    /** Polled between entries; extraction stops early (leaving a partial temp dir) when true. */
    boolean isCancelled();

    /** Adapter reporting entries through the indicator's fraction / text2 / isCanceled. */
    static VfsOpenProgress fromIndicator(ProgressIndicator indicator) {
        return new VfsOpenProgress() {
            @Override
            public void onEntry(int index, int total, String name) {
                if (total > 0) {
                    if (indicator.isIndeterminate()) {
                        indicator.setIndeterminate(false);
                    }
                    indicator.setFraction((double)index / total);
                }
                indicator.setText2(name);
            }

            @Override
            public boolean isCancelled() {
                return indicator.isCanceled();
            }
        };
    }

    /**
     * Adapter for the thread-bound indicator, used as the fallback when no explicit hook is
     * supplied. Returns a no-op when no indicator is bound to the current thread.
     */
    static VfsOpenProgress fromCurrentThread() {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        return indicator != null ? fromIndicator(indicator) : NOOP;
    }
}
