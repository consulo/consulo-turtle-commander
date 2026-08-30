package consulo.turtlecommander.ui;

import consulo.platform.base.icon.PlatformIconGroup;

/**
 * The tab strip's two icon buttons.
 *
 * <p>Consulo's icon library has no hovered "close" variant, so the close button reuses the plain
 * close icon for both states; the hand cursor still signals interactivity. The "new tab" button
 * keeps its inline/solid pair, which does exist.
 */
public final class TabStripButtons {
    private TabStripButtons() {
    }

    public static IconHoverButton newTab(Runnable onClick) {
        return new IconHoverButton(PlatformIconGroup.generalInlineadd(), PlatformIconGroup.generalAdd(),
            "New tab", onClick);
    }

    public static IconHoverButton closeTab(Runnable onClose) {
        return new IconHoverButton(PlatformIconGroup.actionsClose(), PlatformIconGroup.actionsClose(),
            null, onClose);
    }
}
