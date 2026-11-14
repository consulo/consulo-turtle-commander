package consulo.turtlecommander.filetype;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationGroupContributor;

import java.util.function.Consumer;

/**
 * Registers the plugin's notification group - an unregistered one shows its notifications with an error.
 */
@ExtensionImpl
public class TurtleCommanderNotificationGroupContributor implements NotificationGroupContributor {
    @Override
    public void contribute(Consumer<NotificationGroup> registrator) {
        registrator.accept(TurtleCommanderNotifications.group());
    }
}
