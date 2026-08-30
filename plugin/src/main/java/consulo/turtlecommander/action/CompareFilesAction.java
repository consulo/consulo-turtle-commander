package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.diff.DiffManager;
import consulo.diff.DiffContentFactory;
import consulo.diff.content.DiffContent;
import consulo.diff.request.SimpleDiffRequest;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.ui.FileManagerPanel;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabSelectionUtil;
import consulo.ui.ex.action.AnActionEvent;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Diffs two files: either two selected in one panel, or one selected in each. In the cross-panel
 * case the left panel's file always takes the left side, so the comparison reads the way the
 * windows are arranged.
 */
@ActionImpl(id = "TurtleCommander.CompareFiles")
public class CompareFilesAction extends EdtAction {
    public CompareFilesAction() {
        super(LocalizeValue.localizeTODO("Compare Files"));
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(resolvePair(e) != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        FileEntry[] pair = resolvePair(e);
        if (project == null || pair == null) {
            return;
        }
        VirtualFile left = LocalFileSystem.getInstance().findFileByNioFile(pair[0].path());
        VirtualFile right = LocalFileSystem.getInstance().findFileByNioFile(pair[1].path());
        if (left == null || right == null) {
            return;
        }

        DiffContentFactory factory = DiffContentFactory.getInstance();
        DiffContent leftContent = factory.create(project, left);
        DiffContent rightContent = factory.create(project, right);
        DiffManager.getInstance().showDiff(project, new SimpleDiffRequest(
            pair[0].name() + " vs " + pair[1].name(),
            leftContent, rightContent, pair[0].name(), pair[1].name()));
    }

    private static FileEntry @Nullable [] resolvePair(AnActionEvent e) {
        FileTab tab = FileContextMenuState.getClickedTab(e);
        if (tab == null) {
            return null;
        }
        List<FileEntry> selected = comparableEntries(tab);
        if (selected.size() == 2) {
            return new FileEntry[]{selected.get(0), selected.get(1)};
        }
        if (selected.size() != 1) {
            return null;
        }

        FileManagerStateService stateService = ActionTargets.stateService(e);
        if (stateService == null) {
            return null;
        }
        FileManagerPanel leftPanel = stateService.getLeftPanel();
        FileManagerPanel rightPanel = stateService.getRightPanel();
        boolean clickedIsLeft = leftPanel != null && leftPanel.getActiveTab() == tab;
        FileManagerPanel otherPanel = clickedIsLeft ? rightPanel : leftPanel;
        FileTab otherTab = otherPanel != null ? otherPanel.getActiveTab() : null;
        if (otherTab == null) {
            return null;
        }
        List<FileEntry> otherSelected = comparableEntries(otherTab);
        if (otherSelected.size() != 1) {
            return null;
        }
        return clickedIsLeft
            ? new FileEntry[]{selected.get(0), otherSelected.get(0)}
            : new FileEntry[]{otherSelected.get(0), selected.get(0)};
    }

    private static List<FileEntry> comparableEntries(FileTab tab) {
        List<FileEntry> files = new ArrayList<>();
        for (FileEntry entry : FileTabSelectionUtil.getSelectedEntries(tab)) {
            if (!entry.directory() && !entry.parentLink()) {
                files.add(entry);
            }
        }
        return files;
    }
}
