package consulo.turtlecommander.model;

import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Capacity of one mounted volume, as shown by the drive-space dialog. */
public record DriveInfo(String label, long totalSpace, long freeSpace, long usedSpace) {
    /**
     * Identifies a volume across mount points. {@link FileStore#equals} is per mount, so two btrfs
     * subvolumes of one partition ("/" and "/home") would count as two drives.
     */
    private record VolumeKey(String name, long totalSpace) {
    }

    /**
     * One entry per distinct volume among the drive selector's roots. Roots on the same volume (the home
     * folder on the system disk, a plain folder under a mount directory) are reported once, under the
     * first root that reached it. Volumes that cannot be stat'd are skipped rather than reported as
     * zero-sized.
     */
    public static List<DriveInfo> collectAll(List<FileRoot> roots) {
        List<DriveInfo> result = new ArrayList<>();
        Set<VolumeKey> seen = new HashSet<>();
        for (FileRoot root : roots) {
            DriveInfo info = read(root.path(), seen);
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }

    private static @Nullable DriveInfo read(Path path, Set<VolumeKey> seen) {
        // the space of a remote platform's volume is not something a local file store can tell
        if (path.getFileSystem() != FileSystems.getDefault()) {
            return null;
        }
        try {
            FileStore store = Files.getFileStore(path);
            long total = store.getTotalSpace();
            String storeName = store.name();
            if (total <= 0 || !seen.add(new VolumeKey(storeName, total))) {
                return null;
            }
            long free = store.getUsableSpace();
            String baseLabel = baseLabel(path);
            String label = StringUtil.isEmptyOrSpaces(storeName) ? baseLabel : baseLabel + " (" + storeName + ")";
            return new DriveInfo(label, total, free, total - free);
        }
        catch (Exception e) {
            return null;
        }
    }

    /** "C:\" reads as "C:"; "/" stays "/" rather than trimming to nothing. */
    private static String baseLabel(Path path) {
        String text = path.toString();
        String separator = path.getFileSystem().getSeparator();
        return text.length() > separator.length() ? StringUtil.trimEnd(text, separator) : text;
    }
}
