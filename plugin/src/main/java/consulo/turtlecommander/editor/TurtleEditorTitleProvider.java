package consulo.turtlecommander.editor;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.fileEditor.EditorTabTitleProvider;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-08-30
 */
/** Dumb aware, or the restored tab shows the file's id while the project indexes at startup. */
@ExtensionImpl
public class TurtleEditorTitleProvider implements EditorTabTitleProvider, DumbAware {
    @Override
    public @Nullable String getEditorTabTitle(Project project, VirtualFile virtualFile) {
        if (virtualFile instanceof TctVirtualFile) {
            return "Turtle Explorer";
        }
        return null;
    }
}
