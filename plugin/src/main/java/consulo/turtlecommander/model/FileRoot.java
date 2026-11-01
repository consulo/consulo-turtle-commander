package consulo.turtlecommander.model;

import consulo.ui.image.Image;

import java.nio.file.Path;

/**
 * One root the path bar offers: a root location and how to present it.
 *
 * @param location where the root lives - the file chooser tab title of a remote target, empty for this machine
 */
public record FileRoot(Path path, String presentableName, Image icon, String location) {
    public FileRoot(Path path, String presentableName, Image icon) {
        this(path, presentableName, icon, "");
    }
}
