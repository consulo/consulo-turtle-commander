package consulo.turtlecommander.editor;

import consulo.fileEditor.FileEditor;
import consulo.project.Project;
import consulo.turtlecommander.ui.TurtleCommanderToolWindowFactory;
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
    private JComponent myJComponent;
    private final Project myProject;
    private final VirtualFile myVirtualFile;

    public TctFileEditor(Project project, VirtualFile virtualFile) {
        myProject = project;
        myVirtualFile = virtualFile;
    }

    @Override
    public JComponent getComponent() {
        if (myJComponent == null) {
            myJComponent = TurtleCommanderToolWindowFactory.createContent(myProject, myVirtualFile, this);
        }
        return myJComponent;
    }

    @Override
    public Component getUIComponent() {
        return TargetAWT.wrap(getComponent());
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

    }
}
