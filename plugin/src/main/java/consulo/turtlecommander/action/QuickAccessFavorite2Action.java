package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Jumps to favourite slot 2. */
@ActionImpl(id = "TurtleCommander.QuickAccess.2")
public class QuickAccessFavorite2Action extends QuickAccessFavoriteAction {
    public QuickAccessFavorite2Action() {
        super(2);
    }
}
