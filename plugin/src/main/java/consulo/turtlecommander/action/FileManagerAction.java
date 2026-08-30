package consulo.turtlecommander.action;

import consulo.localize.LocalizeValue;
import consulo.turtlecommander.ui.FileTab;
import consulo.ui.ex.action.AnActionEvent;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * An action that operates on the focused tab. Enabled only while one of the tab's views actually
 * holds focus, so a global shortcut doesn't fire while the user is in the editor.
 */
public abstract class FileManagerAction extends EdtAction {
    protected FileManagerAction(LocalizeValue text, @Nullable String defaultShortcut) {
        super(text);
        setDefaultShortcut(defaultShortcut);
    }

    @Override
    public void update(AnActionEvent e) {
        FileTab tab = ActionTargets.findActiveTab(e);
        e.getPresentation().setEnabled(tab != null && tab.hasAnyViewFocus());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        FileTab tab = ActionTargets.findActiveTab(e);
        if (tab != null) {
            performOnTab(tab, e);
        }
    }

    protected abstract void performOnTab(FileTab tab, AnActionEvent e);

    /** Base for the many actions that just delegate to a single tab operation. */
    public abstract static class Simple extends FileManagerAction {
        private final Consumer<FileTab> myOperation;

        protected Simple(LocalizeValue text, @Nullable String defaultShortcut, Consumer<FileTab> operation) {
            super(text, defaultShortcut);
            myOperation = operation;
        }

        @Override
        protected void performOnTab(FileTab tab, AnActionEvent e) {
            myOperation.accept(tab);
        }
    }

    /**
     * An action that only needs the tool window to be active rather than a focused view — panel and
     * tab management, the command line, the drive selectors.
     */
    public abstract static class ToolWindowScoped extends EdtAction {
        protected ToolWindowScoped(LocalizeValue text, @Nullable String defaultShortcut) {
            super(text);
            setDefaultShortcut(defaultShortcut);
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(ActionTargets.isToolWindowActive(e));
        }
    }
}
