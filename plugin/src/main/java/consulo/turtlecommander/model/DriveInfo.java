package consulo.turtlecommander.model;

import consulo.application.util.SystemInfo;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Capacity of one mounted volume, as shown by the drive-space dialog. */
public record DriveInfo(String label, long totalSpace, long freeSpace, long usedSpace) {
    /** Mount points scanned in addition to the filesystem roots on non-Windows hosts. */
    private static final List<String> UNIX_MOUNT_DIRS = List.of("/Volumes", "/media", "/mnt");

    /**
     * Every accessible volume. Roots come from {@link File#listRoots()}, which on Unix is just
     * {@code /} — removable media and network shares live under the mount directories, so those are
     * walked too. Volumes that cannot be stat'd are skipped rather than reported as zero-sized.
     */
    public static List<DriveInfo> collectAll() {
        List<DriveInfo> result = new ArrayList<>();
        for (File root : File.listRoots()) {
            DriveInfo info = read(root.toPath(), StringUtil.trimEnd(root.getAbsolutePath(), File.separator));
            if (info != null) {
                result.add(info);
            }
        }

        if (SystemInfo.isWindows) {
            return result;
        }
        for (String mountDir : UNIX_MOUNT_DIRS) {
            Path dir = Path.of(mountDir);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path entry : stream) {
                    if (!Files.isDirectory(entry) || containsMount(result, entry)) {
                        continue;
                    }
                    DriveInfo info = read(entry, entry.toString());
                    if (info != null) {
                        result.add(info);
                    }
                }
            }
            catch (Exception e) {
                // Unreadable mount directory — nothing to add from it.
            }
        }
        return result;
    }

    private static boolean containsMount(List<DriveInfo> collected, Path path) {
        String prefix = path.toString();
        for (DriveInfo info : collected) {
            if (info.label().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable DriveInfo read(Path path, String baseLabel) {
        try {
            FileStore store = Files.getFileStore(path);
            long total = store.getTotalSpace();
            if (total <= 0) {
                return null;
            }
            long free = store.getUsableSpace();
            String storeName = store.name();
            String label = StringUtil.isEmptyOrSpaces(storeName) ? baseLabel : baseLabel + " (" + storeName + ")";
            return new DriveInfo(label, total, free, total - free);
        }
        catch (Exception e) {
            return null;
        }
    }
}
