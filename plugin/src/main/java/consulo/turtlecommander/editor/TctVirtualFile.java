package consulo.turtlecommander.editor;

import consulo.virtualFileSystem.VirtualFileSystem;
import consulo.virtualFileSystem.VirtualFileWithoutContent;
import consulo.virtualFileSystem.light.LightVirtualFileBase;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * @author VISTALL
 * @since 2026-08-30
 */
public class TctVirtualFile extends LightVirtualFileBase implements VirtualFileWithoutContent {
    private final UUID myUuid;
    private final String myPath;
    private final VirtualFileSystem myFileSystem;

    public TctVirtualFile(UUID uuid, String path, VirtualFileSystem fileSystem) {
        super(uuid.toString(), TctVirtualFileType.INSTANCE, System.currentTimeMillis());
        myUuid = uuid;
        myPath = path;
        myFileSystem = fileSystem;
    }

    @Override
    public String getPath() {
        return myPath;
    }

    @Override
    public VirtualFileSystem getFileSystem() {
        return myFileSystem;
    }

    /**
     * Equal by the id in its url: the file system makes a new instance per lookup, and the editor manager restoring
     * the tab after a restart, or opening it again, must find the one it already has.
     */
    @Override
    public boolean equals(@Nullable Object obj) {
        return obj == this || obj instanceof TctVirtualFile that && myUuid.equals(that.myUuid);
    }

    @Override
    public int hashCode() {
        return myUuid.hashCode();
    }

    @Override
    public OutputStream getOutputStream(Object o, long l, long l1) throws IOException {
        throw new IOException("unsupported");
    }

    @Override
    public byte[] contentsToByteArray() throws IOException {
        // asked for its length when the tab icon is resolved - the explorer has no content, not an unreadable one
        return new byte[0];
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return InputStream.nullInputStream();
    }
}
