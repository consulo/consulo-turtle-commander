package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileManagerTab;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.InputBoxBuilder;
import consulo.ui.annotation.RequiredUIAccess;
import java.util.function.Consumer;

/** Marks every displayed file matching a glob mask. */
@ActionImpl(id = "TurtleCommander.SelectByPattern")
public class SelectByPatternAction extends FileManagerAction {
    static final String MASK_PROMPT = "Mask (e.g. *.txt, separate multiple patterns with ,):";

    public SelectByPatternAction() {
        super(LocalizeValue.localizeTODO("Select by Pattern..."), "ADD");
    }

    @Override
    protected void performOnTab(FileManagerTab tab, AnActionEvent e) {
        askForMask("Select Files", mask -> tab.markByMask(mask, true, false));
    }

    /** Hands the trimmed mask to {@code onMask}; a cancelled or blank answer does nothing. */
    @RequiredUIAccess
    static void askForMask(String title, Consumer<String> onMask) {
        InputBoxBuilder.text()
            .title(LocalizeValue.localizeTODO(title))
            .text(LocalizeValue.localizeTODO(MASK_PROMPT))
            .asQuestion()
            .value("*.*")
            .showAsync()
            .whenComplete((mask, error) -> {
                if (error == null && mask != null && !mask.isBlank()) {
                    onMask.accept(mask.trim());
                }
            });
    }
}
