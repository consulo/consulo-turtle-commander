package consulo.turtlecommander.vfs;

import consulo.logging.Logger;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;

/**
 * One level of a nested-archive stack: the mounted filesystem, where its archive lives inside the
 * parent level, and (for every level but the outermost) the temp copy of that archive.
 */
public class VfsStackEntry {
    private static final Logger LOG = Logger.getInstance(VfsStackEntry.class);

    private final VirtualFileSystem myVfs;
    private final @Nullable File myTempFile;

    private Path myParentPath;

    public VfsStackEntry(VirtualFileSystem vfs, Path parentPath, @Nullable File tempFile) {
        myVfs = vfs;
        myParentPath = parentPath;
        myTempFile = tempFile;
    }

    public VirtualFileSystem getVfs() {
        return myVfs;
    }

    public Path getParentPath() {
        return myParentPath;
    }

    public void setParentPath(Path parentPath) {
        myParentPath = parentPath;
    }

    public @Nullable File getTempFile() {
        return myTempFile;
    }

    public void cleanupTempFile() {
        if (myTempFile == null) {
            return;
        }
        try {
            if (!myTempFile.delete()) {
                LOG.debug("Failed to delete temp archive " + myTempFile);
            }
            File parent = myTempFile.getParentFile();
            if (parent != null) {
                parent.delete();
            }
        }
        catch (Exception e) {
            LOG.debug("Failed to clean up temp archive " + myTempFile, e);
        }
    }
}
