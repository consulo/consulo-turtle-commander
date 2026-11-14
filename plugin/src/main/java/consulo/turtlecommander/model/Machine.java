package consulo.turtlecommander.model;

import consulo.fileChooser.UniversalFileChooserContributor;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

/**
 * A machine a panel can show - this one, or another one the file chooser lists as a tab of its own.
 *
 * @param location the machine's name in {@link FileRoot#location()} - empty for this machine
 * @param contributor the file chooser's tab of the machine - it connects to a remote machine and knows its paths
 */
public record Machine(String location, LocalizeValue title, @Nullable Image icon, UniversalFileChooserContributor contributor) {
    public boolean isLocal() {
        return location.isEmpty();
    }
}
