package consulo.turtlecommander.filetype;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.fileEditor.EditorNotificationBuilder;
import consulo.fileEditor.EditorNotificationProvider;
import consulo.fileEditor.FileEditor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.NotificationType;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/** Offers a one-click import when a {@code .tctheme} file is opened in the editor. */
@ExtensionImpl
public class TctThemeEditorNotificationProvider implements EditorNotificationProvider, DumbAware {
    private final Project myProject;

    @Inject
    public TctThemeEditorNotificationProvider(Project project) {
        myProject = project;
    }

    @Override
    public String getId() {
        return "turtle-commander-theme";
    }

    @RequiredReadAction
    @Override
    public @Nullable EditorNotificationBuilder buildNotification(VirtualFile file, FileEditor fileEditor,
                                                                 Supplier<EditorNotificationBuilder> builderFactory) {
        if (file.getFileType() != TctThemeFileType.INSTANCE) {
            return null;
        }

        EditorNotificationBuilder builder = builderFactory.get();
        builder.withType(NotificationType.INFO);
        builder.withText(LocalizeValue.localizeTODO("Turtle Commander theme file."));
        builder.withAction(LocalizeValue.localizeTODO("Import into Turtle Commander"),
            event -> TctThemeImporter.importFromFile(myProject, file));
        return builder;
    }
}
