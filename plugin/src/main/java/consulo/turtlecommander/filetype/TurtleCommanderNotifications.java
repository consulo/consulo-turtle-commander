package consulo.turtlecommander.filetype;

import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationType;

/**
 * The plugin's balloon notification group. IntelliJ declared this in plugin.xml and looked it up by
 * id through NotificationGroupManager; Consulo builds the group directly.
 */
public final class TurtleCommanderNotifications {
    private static final NotificationGroup GROUP =
        NotificationGroup.balloonGroup("TurtleCommander", LocalizeValue.localizeTODO("Turtle Commander"));

    private TurtleCommanderNotifications() {
    }

    public static NotificationGroup group() {
        return GROUP;
    }

    public static void error(Project project, String content) {
        GROUP.createNotification(content, NotificationType.ERROR).notify(project);
    }

    public static void info(Project project, String content) {
        GROUP.createNotification(content, NotificationType.INFORMATION).notify(project);
    }

    public static void warn(Project project, String content) {
        GROUP.createNotification(content, NotificationType.WARNING).notify(project);
    }
}
