package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabSelectionUtil;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.Messages;
import org.jspecify.annotations.Nullable;

/** Marks every displayed file matching a glob mask. */
@ActionImpl(id = "TurtleCommander.SelectByPattern")
public class SelectByPatternAction extends FileManagerAction {
    static final String MASK_PROMPT = "Mask (e.g. *.txt, separate multiple patterns with ,):";

    public SelectByPatternAction() {
        super(LocalizeValue.localizeTODO("Select by Pattern..."), "ADD");
    }

    @Override
    protected void performOnTab(FileTab tab, AnActionEvent e) {
        String mask = askForMask(e.getData(Project.KEY), "Select Files");
        if (mask != null) {
            FileTabSelectionUtil.selectByMask(tab, mask, false);
        }
    }

    /** Returns the trimmed mask, or null when the user cancelled or left it blank. */
    static @Nullable String askForMask(@Nullable Project project, String title) {
        String mask = Messages.showInputDialog(project, MASK_PROMPT, title,
            Messages.getQuestionIcon(), "*.*", null);
        return mask != null && !mask.isBlank() ? mask.trim() : null;
    }
}
