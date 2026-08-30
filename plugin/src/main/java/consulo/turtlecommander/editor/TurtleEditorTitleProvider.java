package consulo.turtlecommander.editor;

import consulo.annotation.component.ExtensionImpl;
import consulo.fileEditor.EditorTabTitleProvider;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-08-30
 */
@ExtensionImpl
public class TurtleEditorTitleProvider implements EditorTabTitleProvider {
    @Override
    public @Nullable String getEditorTabTitle(Project project, VirtualFile virtualFile) {
        if (virtualFile instanceof TctVirtualFile) {
            return "Turtle Explorer";
        }
        return null;
    }
}
