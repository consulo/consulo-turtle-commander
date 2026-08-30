package consulo.turtlecommander.ui;

import consulo.turtlecommander.util.DriveLabels;
import org.jspecify.annotations.Nullable;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;

/** Renders a drive root through {@link DriveLabels}, so "C:\" shows as "Local Disk (C:)". */
public class DriveComboRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(@Nullable JList<?> list, @Nullable Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        String display = DriveLabels.getDisplayText(value instanceof String path ? path : "");
        return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus);
    }
}
