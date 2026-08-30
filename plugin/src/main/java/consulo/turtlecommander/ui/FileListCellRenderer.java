package consulo.turtlecommander.ui;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.settings.ResolvedStyle;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.ui.ex.awt.speedSearch.SpeedSearchUtil;
import org.jspecify.annotations.Nullable;

import javax.swing.JList;
import java.awt.Color;

/** The flat list view: icon plus name, with the same mark/cursor colour ladder as the table. */
public class FileListCellRenderer extends ColoredListCellRenderer<FileEntry> {
    private final FileTab myTab;

    public FileListCellRenderer(FileTab tab) {
        myTab = tab;
    }

    @Override
    protected void customizeCellRenderer(JList<? extends FileEntry> list, @Nullable FileEntry value, int index,
                                         boolean selected, boolean hasFocus) {
        if (value == null) {
            return;
        }

        boolean marked = myTab.getMarkedPaths().contains(value.path());
        boolean cursor = index == list.getSelectionModel().getLeadSelectionIndex();
        boolean highlighting = myTab.isEnableFileNameHighlighting();
        ResolvedStyle resolved = FileStyleUtil.resolvedStyleFor(value, highlighting);

        setIcon(FileEntryIconUtil.imageFor(value, highlighting, resolved));

        if (marked && cursor) {
            setBackground(MarkColors.MARK_CURSOR_BACKGROUND);
        }
        else if (marked) {
            setBackground(MarkColors.MARK_BACKGROUND);
        }
        else if (cursor) {
            setBackground(list.getSelectionBackground());
        }
        else if (!selected) {
            Color ruleBackground = FileStyleUtil.backgroundColorOf(resolved);
            if (ruleBackground != null) {
                setBackground(ruleBackground);
            }
        }

        Color fg;
        if (marked) {
            fg = list.getForeground();
        }
        else if (cursor) {
            fg = list.getSelectionForeground();
        }
        else if (selected) {
            // Keep ColoredListCellRenderer's default selection foreground.
            fg = null;
        }
        else {
            fg = FileStyleUtil.fontColorOf(resolved);
        }

        append(value.name(),
            FileStyleUtil.nameTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fg, resolved.strikethrough()));
        SpeedSearchUtil.applySpeedSearchHighlighting(list, this, true, selected);
    }
}
