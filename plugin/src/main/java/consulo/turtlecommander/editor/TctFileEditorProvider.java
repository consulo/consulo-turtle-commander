package consulo.turtlecommander.editor;

import consulo.annotation.component.ExtensionImpl;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorPolicy;
import consulo.fileEditor.FileEditorProvider;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.virtualFileSystem.VirtualFile;

/**
 * @author VISTALL
 * @since 2026-08-30
 */
@ExtensionImpl
public class TctFileEditorProvider implements FileEditorProvider {
    @Override
    public boolean accept(Project project, VirtualFile virtualFile) {
        return virtualFile instanceof TctVirtualFile;
    }

    @RequiredUIAccess
    @Override
    public FileEditor createEditor(Project project, VirtualFile virtualFile) {
        return new TctFileEditor(project, virtualFile);
    }

    @Override
    public String getEditorTypeId() {
        return "tct-file-editor";
    }

    @Override
    public FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }
}
