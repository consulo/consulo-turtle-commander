package consulo.turtlecommander.editor;

import consulo.annotation.component.ExtensionImpl;
import consulo.virtualFileSystem.BaseVirtualFileSystem;
import consulo.virtualFileSystem.HiddenFileSystem;
import consulo.virtualFileSystem.NonPhysicalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * @author VISTALL
 * @since 2026-08-30
 */
@ExtensionImpl
public class TctVirtualFileSystem extends BaseVirtualFileSystem implements NonPhysicalFileSystem, HiddenFileSystem {
    public static final String PROTOCOL = "tct-file";

    @Override
    public String getProtocol() {
        return PROTOCOL;
    }

    @Override
    public @Nullable VirtualFile findFileByPath(String path) {
        UUID uuid = UUID.fromString(path.substring(1));
        return new TctVirtualFile(uuid, path, this);
    }

    @Override
    public void refresh(boolean b) {

    }

    @Override
    public @Nullable VirtualFile refreshAndFindFileByPath(String path) {
        return findFileByPath(path);
    }
}
