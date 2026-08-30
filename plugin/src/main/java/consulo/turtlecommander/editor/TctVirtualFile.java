package consulo.turtlecommander.editor;

import consulo.virtualFileSystem.VirtualFileSystem;
import consulo.virtualFileSystem.VirtualFileWithoutContent;
import consulo.virtualFileSystem.light.LightVirtualFileBase;

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

    @Override
    public OutputStream getOutputStream(Object o, long l, long l1) throws IOException {
        throw new IOException("unsupported");
    }

    @Override
    public byte[] contentsToByteArray() throws IOException {
        throw new IOException("unsupported");
    }

    @Override
    public InputStream getInputStream() throws IOException {
        throw new IOException("unsupported");
    }
}
