package consulo.turtlecommander.ui;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * The tabs of one side of the file manager, whichever UI draws them - what the tab actions and the tab context menu
 * work on. The Swing {@link FileManagerPanel} and the unified {@link UnifiedFilePane} both are one.
 */
public interface FileManagerTabStrip {
    int getRealTabCount();

    @Nullable FileManagerTab getTabAt(int index);

    /** The index of the tab in use, or -1. */
    int getActiveTabIndex();

    /** A strip keeps at least one tab - closing the last one does nothing. */
    void closeTab(int tabIndex);

    void closeOtherTabs(int tabIndex);

    /** Closes every tab but one, which is left on its folder. */
    void closeAllTabs();

    void closeTabsToTheLeft(int tabIndex);

    void closeTabsToTheRight(int tabIndex);

    void duplicateTab(int tabIndex);

    /** Whether another tab shows the folder of the tab at {@code tabIndex}. */
    boolean hasDuplicateTabs(int tabIndex);

    /** Closes the other tabs showing the folder of the tab at {@code tabIndex}. */
    void closeDuplicateTabs(int tabIndex);

    boolean hasClosedTabs();

    void reopenLastClosedTab();

    void openDirectoryInNewTab(Path path, @Nullable String selectName);
}
