package consulo.turtlecommander.ui;

import consulo.localize.LocalizeValue;
import consulo.turtlecommander.settings.ComponentStyle;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.Space;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.color.ColorValue;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.layout.DockLayout;
import org.jspecify.annotations.Nullable;

import java.awt.Color;

/**
 * The line under a listing: what the directory holds and what is selected on the left, the free space of its volume on
 * the right - a click on which opens the space of every drive.
 */
public final class UnifiedStatusBar {
    private final Label mySummary = Label.create(LocalizeValue.empty());
    private final Label myFreeSpace = Label.create(LocalizeValue.empty());
    private final DockLayout myRoot = DockLayout.create();

    @RequiredUIAccess
    public UnifiedStatusBar() {
        myFreeSpace.setToolTipText(LocalizeValue.localizeTODO("Click to view drive space"));
        // the summary takes what the free space leaves and is cut off there - docked left, it kept its full width and a
        // narrow pane drew it over the free space
        myRoot.center(mySummary).right(myFreeSpace);
        myRoot.paddingBuilder().verticalSet(Space.X_SMALL).horizontalSet(Space.MEDIUM).apply();
    }

    public Component getComponent() {
        return myRoot;
    }

    @RequiredUIAccess
    public void setSummary(String text) {
        mySummary.setText(LocalizeValue.of(text));
    }

    @RequiredUIAccess
    public void setFreeSpace(String text) {
        myFreeSpace.setText(LocalizeValue.of(text));
    }

    public void setOnFreeSpaceClick(Runnable onClick) {
        myFreeSpace.addClickListener(event -> onClick.run());
    }

    /** The theme's colors; its font has no unified counterpart yet. */
    @RequiredUIAccess
    public void applyStyle(ComponentStyle style) {
        ColorValue foreground = toColorValue(style.parsedFontColor());
        mySummary.setForegroundColor(foreground);
        myFreeSpace.setForegroundColor(foreground);
        myRoot.setBackgroundColor(toColorValue(style.parsedBackgroundColor()));
    }

    static @Nullable ColorValue toColorValue(@Nullable Color color) {
        return color == null ? null : TargetAWT.from(color);
    }
}
