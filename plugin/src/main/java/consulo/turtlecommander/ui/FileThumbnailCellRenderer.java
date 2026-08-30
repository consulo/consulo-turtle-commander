package consulo.turtlecommander.ui;

import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.service.ThumbnailCache;
import consulo.turtlecommander.settings.ResolvedStyle;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import org.jspecify.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

/**
 * The thumbnail grid: a large icon over a centred, truncated name.
 *
 * <p>Image files get a real thumbnail once one has been generated; until then — and for everything
 * else — the file-type icon is scaled up. Generation is requested lazily and only while the cell is
 * on screen, so scrolling past a directory of photos doesn't queue work for all of them.
 */
public class FileThumbnailCellRenderer implements ListCellRenderer<FileEntry> {
    private static final int NAME_MAX_LENGTH = 16;
    private static final int NAME_TRUNCATED_LENGTH = 14;
    private static final float ICON_SCALE = 2.0f;

    private final FileTab myTab;

    private final JPanel myPanel = new JPanel(new BorderLayout());
    private final JLabel myIconLabel = new JLabel();
    private final JLabel myNameLabel = new JLabel();

    public FileThumbnailCellRenderer(FileTab tab) {
        myTab = tab;

        myPanel.setOpaque(true);
        myPanel.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));
        myIconLabel.setHorizontalAlignment(JLabel.CENTER);
        myIconLabel.setVerticalAlignment(JLabel.CENTER);
        myNameLabel.setHorizontalAlignment(JLabel.CENTER);

        myPanel.add(myIconLabel, BorderLayout.CENTER);
        myPanel.add(myNameLabel, BorderLayout.SOUTH);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends FileEntry> list, @Nullable FileEntry value,
                                                  int index, boolean isSelected, boolean cellHasFocus) {
        if (value == null) {
            return myPanel;
        }

        boolean highlighting = myTab.isEnableFileNameHighlighting();
        ResolvedStyle resolved = FileStyleUtil.resolvedStyleFor(value, highlighting);

        Icon thumbnail = thumbnailFor(value, index);
        if (thumbnail != null) {
            myIconLabel.setIcon(thumbnail);
        }
        else {
            Image icon = FileEntryIconUtil.imageFor(value, highlighting, resolved);
            myIconLabel.setIcon(TargetAWT.to(ImageEffects.resize(icon, ICON_SCALE)));
        }

        String name = value.name();
        String displayName = name.length() > NAME_MAX_LENGTH
            ? name.substring(0, NAME_TRUNCATED_LENGTH) + "\u2026"
            : name;
        myNameLabel.setText(FileStyleUtil.highlightSpeedSearchMatches(list, displayName));
        myNameLabel.setToolTipText(name);
        myNameLabel.setFont(FileStyleUtil.strikeFont(list.getFont(),
            Boolean.TRUE.equals(resolved.strikethrough())));

        applyColors(list, value, index, isSelected, resolved);
        return myPanel;
    }

    private @Nullable Icon thumbnailFor(FileEntry entry, int index) {
        if (entry.directory() || entry.parentLink() || myTab.isInsideArchive()
            || !ThumbnailCache.isImageFile(entry.name())) {
            return null;
        }

        ThumbnailCache cache = ThumbnailCache.getInstance();
        Icon cached = cache.getCachedThumbnail(entry.path());
        if (cached == null) {
            cache.requestThumbnail(entry.path(), entry.lastModified(),
                () -> index >= myTab.getThumbnailList().getFirstVisibleIndex()
                    && index <= myTab.getThumbnailList().getLastVisibleIndex(),
                () -> myTab.getThumbnailList().repaint());
        }
        return cached;
    }

    private void applyColors(JList<? extends FileEntry> list, FileEntry entry, int index, boolean isSelected,
                             ResolvedStyle resolved) {
        boolean marked = myTab.getMarkedPaths().contains(entry.path());
        boolean cursor = index == list.getSelectionModel().getLeadSelectionIndex();

        if (marked && cursor) {
            myPanel.setBackground(MarkColors.MARK_CURSOR_BACKGROUND);
            myNameLabel.setForeground(list.getForeground());
        }
        else if (marked) {
            myPanel.setBackground(MarkColors.MARK_BACKGROUND);
            myNameLabel.setForeground(list.getForeground());
        }
        else if (cursor || isSelected) {
            myPanel.setBackground(list.getSelectionBackground());
            myNameLabel.setForeground(list.getSelectionForeground());
        }
        else {
            Color ruleBackground = FileStyleUtil.backgroundColorOf(resolved);
            Color ruleForeground = FileStyleUtil.fontColorOf(resolved);
            myPanel.setBackground(ruleBackground != null ? ruleBackground : list.getBackground());
            myNameLabel.setForeground(ruleForeground != null ? ruleForeground : list.getForeground());
        }
    }
}
