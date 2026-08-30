package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Jumps to favourite slot 1. */
@ActionImpl(id = "TurtleCommander.QuickAccess.1")
public class QuickAccessFavorite1Action extends QuickAccessFavoriteAction {
    public QuickAccessFavorite1Action() {
        super(1);
    }
}
