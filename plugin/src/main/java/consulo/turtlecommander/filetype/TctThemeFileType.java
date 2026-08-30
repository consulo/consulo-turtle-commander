package consulo.turtlecommander.filetype;

import consulo.localize.LocalizeValue;
import consulo.turtlecommander.icon.TurtleCommanderIconGroup;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.PlainTextLikeFileType;

/** The exported {@code .tctheme} format, so the IDE can offer to import one from the editor. */
public class TctThemeFileType implements FileType, PlainTextLikeFileType {
    public static final TctThemeFileType INSTANCE = new TctThemeFileType();

    private TctThemeFileType() {
    }

    @Override
    public String getId() {
        return "TURTLE_COMMANDER_THEME";
    }

    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Turtle Commander Theme");
    }

    @Override
    public LocalizeValue getDescription() {
        return LocalizeValue.localizeTODO("Turtle Commander theme");
    }

    @Override
    public String getDefaultExtension() {
        return "tctheme";
    }

    @Override
    public Image getIcon() {
        return TurtleCommanderIconGroup.turtlecommander();
    }

    @Override
    public boolean isBinary() {
        return false;
    }
}
