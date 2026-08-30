package consulo.turtlecommander.ui;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.settings.ResolvedStyle;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.speedSearch.SpeedSearchUtil;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import org.jspecify.annotations.Nullable;

import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.Color;

/** The tree view. Non-entry nodes (the lazy "Loading..." placeholder) render as plain text. */
public class FileTreeCellRenderer extends ColoredTreeCellRenderer {
    private final FileTab myTab;

    public FileTreeCellRenderer(FileTab tab) {
        myTab = tab;
    }

    @Override
    public void customizeCellRenderer(JTree tree, @Nullable Object value, boolean selected, boolean expanded,
                                      boolean leaf, int row, boolean hasFocus) {
        if (!(value instanceof DefaultMutableTreeNode node)) {
            return;
        }
        if (!(node.getUserObject() instanceof FileEntry entry)) {
            append(node.getUserObject() != null ? node.getUserObject().toString() : "");
            return;
        }

        boolean marked = myTab.getMarkedPaths().contains(entry.path());
        boolean cursor = row == tree.getLeadSelectionRow();
        boolean highlighting = myTab.isEnableFileNameHighlighting();
        ResolvedStyle resolved = FileStyleUtil.resolvedStyleFor(entry, highlighting);

        setIcon(FileEntryIconUtil.imageFor(entry, highlighting, resolved));

        if (marked && cursor) {
            setBackground(MarkColors.MARK_CURSOR_BACKGROUND);
        }
        else if (marked) {
            setBackground(MarkColors.MARK_BACKGROUND);
        }
        else if (cursor) {
            Color selectionBackground = UIManager.getColor("Tree.selectionBackground");
            setBackground(selectionBackground != null ? selectionBackground : tree.getBackground());
        }
        else if (!selected) {
            Color ruleBackground = FileStyleUtil.backgroundColorOf(resolved);
            if (ruleBackground != null) {
                setBackground(ruleBackground);
            }
        }

        Color fg;
        if (marked) {
            fg = tree.getForeground();
        }
        else if (cursor) {
            Color selectionForeground = UIManager.getColor("Tree.selectionForeground");
            fg = selectionForeground != null ? selectionForeground : tree.getForeground();
        }
        else if (selected) {
            fg = null;
        }
        else {
            fg = FileStyleUtil.fontColorOf(resolved);
        }

        append(entry.name(),
            FileStyleUtil.nameTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fg, resolved.strikethrough()));
        SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, true, selected);
    }
}
