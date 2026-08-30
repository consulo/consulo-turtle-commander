package consulo.turtlecommander.ui;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.util.FileUtils;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Builds the two status-bar strings: the directory summary and the free-space readout. */
public final class StatusBarTextUtil {
    private StatusBarTextUtil() {
    }

    /**
     * "{@code 3 dir(s), 12 file(s), 4.1 MiB}", with a selection suffix when anything is selected.
     * The parent ".." row is excluded from every count.
     *
     * <p>A selected directory contributes its computed size from {@code directorySizes} when one is
     * known, and zero otherwise — directory sizes are calculated lazily in the background, so an
     * un-calculated directory must not inflate the total.
     */
    public static String summary(List<FileEntry> entries, List<FileEntry> selectedEntries,
                                 Map<Path, Long> directorySizes) {
        int dirs = 0;
        int files = 0;
        long totalSize = 0;

        for (FileEntry entry : entries) {
            if (entry.parentLink()) {
                continue;
            }
            if (entry.directory()) {
                dirs++;
            }
            else {
                files++;
                totalSize += entry.size();
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(dirs).append(" dir(s), ").append(files).append(" file(s), ").append(FileUtils.formatSize(totalSize));

        if (!selectedEntries.isEmpty()) {
            long selectedSize = 0;
            for (FileEntry entry : selectedEntries) {
                if (entry.directory()) {
                    Long known = directorySizes.get(entry.path());
                    selectedSize += known != null ? known : 0L;
                }
                else {
                    selectedSize += entry.size();
                }
            }
            sb.append("  |  ").append(selectedEntries.size()).append(" selected, ")
                .append(FileUtils.formatSize(selectedSize));
        }

        return sb.toString();
    }

    /**
     * "{@code 120.4 GiB of 460.4 GiB free (26%)}", or an empty string when the store can't be
     * queried. Blocking — callers run it off the UI thread, since a dead network mount can hang.
     */
    public static String freeSpace(Path path) {
        try {
            FileStore store = Files.getFileStore(path);
            long usable = store.getUsableSpace();
            long total = store.getTotalSpace();
            long percent = total > 0 ? usable * 100 / total : 0;
            return FileUtils.formatSizeAuto(usable) + " of " + FileUtils.formatSizeAuto(total)
                + " free (" + percent + "%)";
        }
        catch (Exception e) {
            return "";
        }
    }
}
