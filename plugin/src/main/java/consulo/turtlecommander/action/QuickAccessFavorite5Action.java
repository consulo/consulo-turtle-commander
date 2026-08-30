package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Jumps to favourite slot 5. */
@ActionImpl(id = "TurtleCommander.QuickAccess.5")
public class QuickAccessFavorite5Action extends QuickAccessFavoriteAction {
    public QuickAccessFavorite5Action() {
        super(5);
    }
}
