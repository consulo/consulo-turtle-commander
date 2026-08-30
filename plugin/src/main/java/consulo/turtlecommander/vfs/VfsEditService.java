package consulo.turtlecommander.vfs;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.logging.Logger;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Writes files edited out of an archive back into it when the editor saves them.
 *
 * <p>Edits are keyed by the temp file the editor actually has open. A save arrives via the VFS
 * listener, is matched against the tracked entries, and the write-back runs on a pooled thread —
 * never on the UI thread, since it re-packs archives.
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class VfsEditService implements Disposable {
    private static final Logger LOG = Logger.getInstance(VfsEditService.class);

    private final Map<String, VfsEditEntry> myActiveEdits = new ConcurrentHashMap<>();

    public void trackEdit(VfsEditEntry entry) {
        myActiveEdits.put(normalizeKey(entry.getTempFilePath()), entry);
    }

    public void onFileSaved(String filePath) {
        VfsEditEntry entry = myActiveEdits.get(normalizeKey(filePath));
        if (entry == null) {
            return;
        }
        Application.get().executeOnPooledThread(() -> writeBack(entry));
    }

    /** Test-only: whether a tracking entry still exists for the given normalized key. */
    boolean isTrackedForTest(String normalizedKey) {
        return myActiveEdits.containsKey(normalizedKey);
    }

    @Override
    public void dispose() {
        List<VfsEditEntry> snapshot = new ArrayList<>(myActiveEdits.values());
        myActiveEdits.clear();

        for (VfsEditEntry entry : snapshot) {
            try {
                Files.deleteIfExists(entry.getTempFilePath());
                Path parent = entry.getTempFilePath().getParent();
                if (parent != null) {
                    Files.deleteIfExists(parent);
                }
            }
            catch (Exception e) {
                LOG.debug("Failed to clean up temp file: " + entry.getTempFilePath(), e);
            }
        }
    }

    private void writeBack(VfsEditEntry entry) {
        try {
            Lock lock = entry.getVfsWriteLock();
            if (lock == null) {
                writeBackLocked(entry);
            }
            else {
                lock.lock();
                try {
                    writeBackLocked(entry);
                }
                finally {
                    lock.unlock();
                }
            }
            // The entry stays tracked on success: the same file can be saved repeatedly and every
            // save must reach the archive. It is cleaned up in dispose(); a failed write-back is
            // dropped below.
        }
        catch (Exception e) {
            LOG.warn("VFS edit write-back failed", e);
            // Drop a failed edit so a permanently broken entry — say the archive was closed
            // underneath it — doesn't keep retrying, and failing, on every later save.
            myActiveEdits.remove(normalizeKey(entry.getTempFilePath()));
        }
    }

    /**
     * The core write-back. Must run with the entry's lock held (when it has one) so no concurrent
     * refresh closes a parent zip filesystem while this loop is mid-copy into one of its paths.
     */
    private void writeBackLocked(VfsEditEntry entry) throws IOException {
        List<VfsStackEntry> stack = entry.getVfsStack();
        VirtualFileSystem innermostVfs = stack.isEmpty() ? null : stack.get(stack.size() - 1).getVfs();

        // Resolve the destination against the CURRENT innermost root every time. An absolute path
        // captured when the edit was opened may now dangle: the temp dir is deleted and recreated by
        // a flush — this file's own previous save, or another file from the same archive saved
        // first. Re-resolving from the archive-relative path keeps every save landing on the right
        // entry instead of failing and losing the edit.
        Path target = resolveTarget(entry, innermostVfs);
        Files.copy(entry.getTempFilePath(), target, StandardCopyOption.REPLACE_EXISTING);
        entry.setVfsFilePath(target);

        // Capture the current path's relative form BEFORE any flush invalidates it.
        Supplier<String> onBeforeFlush = entry.getOnBeforeFlush();
        String currentPathRel = onBeforeFlush != null ? onBeforeFlush.get() : "";

        if (innermostVfs != null) {
            innermostVfs.flush();
            entry.setVfsFilePath(resolveAgainstRoot(innermostVfs, entry.getVfsFileRelPath()));
        }

        // Write back out through the stack, innermost to outermost.
        for (int i = stack.size() - 1; i > 0; i--) {
            VfsStackEntry stackEntry = stack.get(i);
            if (stackEntry.getTempFile() == null) {
                continue;
            }

            VfsStackEntry parentStackEntry = stack.get(i - 1);
            Files.copy(stackEntry.getTempFile().toPath(), stackEntry.getParentPath(),
                StandardCopyOption.REPLACE_EXISTING);

            String relPath = VfsPaths.vfsRelativePath(parentStackEntry.getVfs().getRoot(), stackEntry.getParentPath());
            parentStackEntry.getVfs().flush();
            stackEntry.setParentPath(resolveAgainstRoot(parentStackEntry.getVfs(), relPath));
        }

        Consumer<String> onAfterFlush = entry.getOnAfterFlush();
        if (onAfterFlush != null) {
            onAfterFlush.accept(currentPathRel);
        }
    }

    private static Path resolveTarget(VfsEditEntry entry, @org.jspecify.annotations.Nullable VirtualFileSystem vfs) {
        if (vfs == null) {
            return entry.getVfsFilePath();
        }
        return resolveAgainstRoot(vfs, entry.getVfsFileRelPath());
    }

    private static Path resolveAgainstRoot(VirtualFileSystem vfs, String relativePath) {
        return relativePath.isEmpty() ? vfs.getRoot() : vfs.getRoot().resolve(relativePath);
    }

    private static String normalizeKey(Path path) {
        return normalizeKey(path.toString());
    }

    private static String normalizeKey(String path) {
        return path.replace('\\', '/');
    }
}
