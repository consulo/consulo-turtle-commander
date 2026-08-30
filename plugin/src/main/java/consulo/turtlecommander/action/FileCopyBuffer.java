package consulo.turtlecommander.action;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.util.UiTask;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Ctrl+C / Ctrl+X clipboard shared by every panel, plus the tool window header's buffer
 * indicator. Listeners fire whenever the contents change so the indicator can refresh.
 */
public final class FileCopyBuffer {
    private static final List<Runnable> ourListeners = new CopyOnWriteArrayList<>();

    private static volatile List<FileEntry> ourEntries = List.of();
    private static volatile boolean ourCut;

    private FileCopyBuffer() {
    }

    public static List<FileEntry> getEntries() {
        return ourEntries;
    }

    public static void setEntries(List<FileEntry> entries) {
        ourEntries = List.copyOf(entries);
        for (Runnable listener : ourListeners) {
            listener.run();
        }
    }

    public static boolean isCut() {
        return ourCut;
    }

    public static void setCut(boolean cut) {
        ourCut = cut;
    }

    public static void addChangeListener(Runnable listener) {
        ourListeners.add(listener);
    }

    /**
     * Settles the cut buffer against a finished cut-paste. Existence decides each entry's outcome:
     * a source path that is gone was moved and leaves the buffer, while one still on disk failed,
     * was skipped, or was never reached after a cancel — it stays cut, so pasting again retries just
     * the remainder. A partially moved directory keeps its leftovers the same way.
     *
     * <p>A no-op when the buffer no longer holds the exact list this paste consumed: a cut or copy
     * made while the move was running wins.
     */
    public static void pruneMovedEntries(List<FileEntry> consumed) {
        List<FileEntry> remaining = new ArrayList<>();
        for (FileEntry entry : consumed) {
            if (Files.exists(entry.path(), LinkOption.NOFOLLOW_LINKS)) {
                remaining.add(entry);
            }
        }
        UiTask.ui(() -> {
            if (ourCut && ourEntries.equals(consumed)) {
                setEntries(remaining);
            }
        });
    }
}
