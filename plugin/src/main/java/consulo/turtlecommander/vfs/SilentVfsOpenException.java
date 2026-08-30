package consulo.turtlecommander.vfs;

/**
 * Thrown by {@link VirtualFileSystemProvider#create} when a file's extension is recognised but its
 * contents match no supported archive format. Callers treat this as "leave the file as it is": no
 * VFS is opened and no error is shown. Contrast with the generic exceptions thrown for genuinely
 * corrupt archives, which do surface an error to the user.
 */
public class SilentVfsOpenException extends RuntimeException {
    public SilentVfsOpenException() {
        super(null, null, false, false);
    }
}
