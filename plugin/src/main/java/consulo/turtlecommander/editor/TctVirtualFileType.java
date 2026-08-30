package consulo.turtlecommander.editor;

import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.fileType.FileType;

/**
 * @author VISTALL
 * @since 2026-08-30
 */
public class TctVirtualFileType implements FileType {
    public static final TctVirtualFileType INSTANCE = new TctVirtualFileType();

    @Override
    public String getId() {
        return TctVirtualFileSystem.PROTOCOL;
    }

    @Override
    public boolean isBinary() {
        return true;
    }

    @Override
    public LocalizeValue getDescription() {
        return LocalizeValue.empty();
    }

    @Override
    public Image getIcon() {
        return PlatformIconGroup.nodesFolder();
    }
}
