package consulo.turtlecommander.util;

import consulo.application.util.SystemInfo;
import consulo.util.lang.StringUtil;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Display labels for drive roots in the drive selector ("C:\" → "Local Disk (C:)"-style).
 *
 * <p>{@code FileSystemView.getSystemDisplayName} is a Win32 shell call that can block for
 * seconds on disconnected network drives or empty card-reader slots, so results are cached and
 * the shared drive-roots poller warms the cache on an IO thread before any combo renders a root
 * on the UI thread.
 */
public final class DriveLabels {
    private static final Pattern DRIVE_ROOT = Pattern.compile("[A-Za-z]:\\\\");

    private static final Map<String, String> ourLabelCache = new ConcurrentHashMap<>();

    private DriveLabels() {
    }

    public static String getDisplayText(String path) {
        if (!SystemInfo.isWindows || path.isEmpty()) {
            return path;
        }
        if (!DRIVE_ROOT.matcher(path).matches()) {
            return path;
        }
        return ourLabelCache.computeIfAbsent(path, DriveLabels::querySystemDisplayName);
    }

    private static String querySystemDisplayName(String path) {
        try {
            String label = FileSystemView.getFileSystemView().getSystemDisplayName(new File(path));
            if (label != null && !label.isBlank() && !label.equals(StringUtil.trimTrailing(path, '\\'))) {
                return label;
            }
        }
        catch (Exception e) {
            // fall through to the raw path
        }
        return path;
    }

}
