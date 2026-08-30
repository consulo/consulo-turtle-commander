package consulo.turtlecommander.filetype;

import consulo.project.Project;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationType;
import consulo.turtlecommander.settings.Theme;
import consulo.turtlecommander.settings.ThemeManager;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.ui.ex.awt.Messages;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** Imports an exported {@code .tctheme} file into the user's theme list. */
public final class TctThemeImporter {
    private static final String TITLE = "Import Theme";

    private TctThemeImporter() {
    }

    public static void importFromFile(@Nullable Project project, VirtualFile file) {
        String text;
        try {
            text = VirtualFileUtil.loadText(file);
        }
        catch (Exception e) {
            Messages.showWarningDialog(project, "Could not read theme file: " + e.getMessage(), TITLE);
            return;
        }

        Theme theme = ThemeManager.importTheme(text);
        if (theme == null) {
            Messages.showWarningDialog(project, "Invalid theme file.", TITLE);
            return;
        }

        TurtleCommanderSettings.State state = TurtleCommanderSettings.getInstance().getState();

        Set<String> allNames = new HashSet<>();
        for (Theme existing : ThemeManager.getAllThemes(state)) {
            allNames.add(existing.name());
        }

        boolean overwrite = false;
        if (allNames.contains(theme.name())) {
            int choice = Messages.showYesNoCancelDialog(project,
                "A theme named \"" + theme.name() + "\" already exists. Overwrite it?",
                TITLE, "Overwrite", "Rename", "Cancel", null);
            if (choice == Messages.YES) {
                overwrite = true;
            }
            else if (choice != Messages.NO) {
                return;
            }
        }

        ThemeManager.ImportOutcome outcome = ThemeManager.importThemeToState(state, theme, overwrite);
        String message = switch (outcome.result()) {
            case REPLACED -> "Theme \"" + outcome.name() + "\" was updated.";
            case RENAMED -> "Theme imported as \"" + outcome.name() + "\" to avoid a name conflict.";
            case IMPORTED -> "Theme \"" + outcome.name() + "\" was imported.";
        };

        TurtleCommanderNotifications.group()
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project);
    }
}
