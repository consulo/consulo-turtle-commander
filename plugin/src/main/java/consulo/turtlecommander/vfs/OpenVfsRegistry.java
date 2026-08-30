package consulo.turtlecommander.vfs;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Registry of currently-open VFS instances, used by consumer sites that hold a
 * {@link Path} but don't know which VFS produced it (content search, hash computation, generic
 * copy). Each {@link AbstractTempDirVirtualFileSystem} registers itself once its temp dir is ready
 * and unregisters on close.
 *
 * <p>Lookup is O(n) over open filesystems — n is the number of archives the user has nested into,
 * typically 1-3. A {@link CopyOnWriteArraySet} keeps reads lock-free; register/unregister are rare
 * relative to materialize calls.
 */
public final class OpenVfsRegistry {
    private static final Set<VirtualFileSystem> ourInstances = new CopyOnWriteArraySet<>();

    private OpenVfsRegistry() {
    }

    public static void register(VirtualFileSystem vfs) {
        ourInstances.add(vfs);
    }

    public static void unregister(VirtualFileSystem vfs) {
        ourInstances.remove(vfs);
    }

    /**
     * Forwards {@code path} to whichever registered VFS claims it. Safe to call with any path,
     * including ones outside every VFS — the lookup returns silently in that case, so callers
     * don't need to guard with an "is inside a VFS" check.
     */
    public static void materializeIfNeeded(Path path) {
        for (VirtualFileSystem vfs : ourInstances) {
            if (vfs.owns(path)) {
                vfs.materialize(path);
                return;
            }
        }
    }

    /**
     * Recursive {@link #materializeIfNeeded}: ensures every file under {@code path} holds real
     * bytes. Needed before a same-filesystem move relocates a lazy-VFS subtree out of its temp dir
     * wholesale — after the move the stubs are no longer under any VFS root, so per-file
     * interception can never run for them again.
     */
    public static void materializeTreeIfNeeded(Path path) {
        VirtualFileSystem owner = null;
        for (VirtualFileSystem vfs : ourInstances) {
            if (vfs.owns(path)) {
                owner = vfs;
                break;
            }
        }
        if (owner == null) {
            return;
        }

        if (!Files.isDirectory(path)) {
            owner.materialize(path);
            return;
        }

        VirtualFileSystem target = owner;
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    target.materialize(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            // A subtree we cannot walk simply stays unmaterialized; the move will copy the stubs.
        }
    }
}
