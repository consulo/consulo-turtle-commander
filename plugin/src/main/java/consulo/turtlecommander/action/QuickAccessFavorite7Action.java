package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Jumps to favourite slot 7. */
@ActionImpl(id = "TurtleCommander.QuickAccess.7")
public class QuickAccessFavorite7Action extends QuickAccessFavoriteAction {
    public QuickAccessFavorite7Action() {
        super(7);
    }
}
