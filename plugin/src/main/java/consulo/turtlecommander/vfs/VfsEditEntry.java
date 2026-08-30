package consulo.turtlecommander.vfs;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A file extracted out of an archive for editing, plus everything needed to write it back. */
public class VfsEditEntry {
    private final Path myTempFilePath;
    private final List<VfsStackEntry> myVfsStack;

    /**
     * Path of the edited file relative to the innermost root, captured while {@code vfsFilePath} was
     * still valid. Write-back re-resolves the destination from this against the <em>current</em>
     * innermost root on every save, so it survives the temp dir being recreated by a flush — whether
     * this file's own earlier save or another file edited from the same archive. Empty when there is
     * no stack.
     */
    private final String myVfsFileRelPath;

    /**
     * Lock owned by the originating tab, serialising all write-back work against the shared stack.
     * Must be held while flushing or copying into any filesystem in the stack; otherwise a
     * concurrent refresh can close a parent zip filesystem mid-copy.
     */
    private final @Nullable Lock myVfsWriteLock;

    private final @Nullable Supplier<String> myOnBeforeFlush;
    private final @Nullable Consumer<String> myOnAfterFlush;

    private Path myVfsFilePath;

    public VfsEditEntry(Path vfsFilePath, Path tempFilePath, List<VfsStackEntry> vfsStack, String vfsFileRelPath,
                        @Nullable Lock vfsWriteLock, @Nullable Supplier<String> onBeforeFlush,
                        @Nullable Consumer<String> onAfterFlush) {
        myVfsFilePath = vfsFilePath;
        myTempFilePath = tempFilePath;
        myVfsStack = vfsStack;
        myVfsFileRelPath = vfsFileRelPath;
        myVfsWriteLock = vfsWriteLock;
        myOnBeforeFlush = onBeforeFlush;
        myOnAfterFlush = onAfterFlush;
    }

    public Path getVfsFilePath() {
        return myVfsFilePath;
    }

    public void setVfsFilePath(Path vfsFilePath) {
        myVfsFilePath = vfsFilePath;
    }

    public Path getTempFilePath() {
        return myTempFilePath;
    }

    public List<VfsStackEntry> getVfsStack() {
        return myVfsStack;
    }

    public String getVfsFileRelPath() {
        return myVfsFileRelPath;
    }

    public @Nullable Lock getVfsWriteLock() {
        return myVfsWriteLock;
    }

    public @Nullable Supplier<String> getOnBeforeFlush() {
        return myOnBeforeFlush;
    }

    public @Nullable Consumer<String> getOnAfterFlush() {
        return myOnAfterFlush;
    }
}
