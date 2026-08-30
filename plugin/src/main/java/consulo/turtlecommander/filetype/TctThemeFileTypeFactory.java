package consulo.turtlecommander.filetype;

import consulo.annotation.component.ExtensionImpl;
import consulo.virtualFileSystem.fileType.FileTypeConsumer;
import consulo.virtualFileSystem.fileType.FileTypeFactory;

@ExtensionImpl
public class TctThemeFileTypeFactory extends FileTypeFactory {
    @Override
    public void createFileTypes(FileTypeConsumer consumer) {
        consumer.consume(TctThemeFileType.INSTANCE, TctThemeFileType.INSTANCE.getDefaultExtension());
    }
}
