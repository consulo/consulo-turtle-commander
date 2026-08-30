package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Jumps to favourite slot 8. */
@ActionImpl(id = "TurtleCommander.QuickAccess.8")
public class QuickAccessFavorite8Action extends QuickAccessFavoriteAction {
    public QuickAccessFavorite8Action() {
        super(8);
    }
}
