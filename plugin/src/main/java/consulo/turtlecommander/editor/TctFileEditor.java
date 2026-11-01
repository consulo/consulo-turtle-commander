package consulo.turtlecommander.editor;

import consulo.fileEditor.FileEditor;
import consulo.project.Project;
import consulo.disposer.Disposer;
import consulo.turtlecommander.ui.UnifiedFileManagerView;
import consulo.ui.annotation.RequiredUIAccess;
import org.jspecify.annotations.Nullable;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.dataholder.UserDataHolderBase;
import consulo.virtualFileSystem.VirtualFile;
import kava.beans.PropertyChangeListener;

import javax.swing.*;

/**
 * @author VISTALL
 * @since 2026-08-30
 */
public class TctFileEditor extends UserDataHolderBase implements FileEditor {
    private @Nullable UnifiedFileManagerView myView;
    private final Project myProject;
    private final VirtualFile myVirtualFile;

    public TctFileEditor(Project project, VirtualFile virtualFile) {
        myProject = project;
        myVirtualFile = virtualFile;
    }

    @Override
    public JComponent getComponent() {
        return (JComponent) TargetAWT.to(getUIComponent());
    }

    /** The file manager in the unified UI, so the editor opens on every frontend. */
    @RequiredUIAccess
    @Override
    public Component getUIComponent() {
        UnifiedFileManagerView view = myView;
        if (view == null) {
            view = new UnifiedFileManagerView(myProject);
            myView = view;
        }
        return view.getComponent();
    }

    @Override
    public String getName() {
        return "Turtle Editor";
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener propertyChangeListener) {

    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener propertyChangeListener) {

    }

    @Override
    public void dispose() {
        UnifiedFileManagerView view = myView;
        if (view != null) {
            Disposer.dispose(view);
        }

    }
}
