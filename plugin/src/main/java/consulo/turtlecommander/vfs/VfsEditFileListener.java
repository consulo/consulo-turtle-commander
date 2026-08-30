package consulo.turtlecommander.vfs;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicImpl;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.virtualFileSystem.event.BulkFileListener;
import consulo.virtualFileSystem.event.VFileContentChangeEvent;
import consulo.virtualFileSystem.event.VFileEvent;

import java.util.List;

/**
 * Routes editor saves of extracted archive entries back into their archive.
 *
 * <p>{@link BulkFileListener} is an application-scoped topic, so this can't be bound to a single
 * project. Each save is offered to every open project's {@link VfsEditService}; a service that
 * isn't tracking the path returns immediately, so at most one actually writes back.
 */
@TopicImpl(ComponentScope.APPLICATION)
public class VfsEditFileListener implements BulkFileListener {
    @Override
    public void after(List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
            if (!(event instanceof VFileContentChangeEvent changeEvent)) {
                continue;
            }
            String path = changeEvent.getFile().getPath();
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (!project.isDisposed()) {
                    project.getInstance(VfsEditService.class).onFileSaved(path);
                }
            }
        }
    }
}
