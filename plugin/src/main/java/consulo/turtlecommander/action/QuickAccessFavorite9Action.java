package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Jumps to favourite slot 9. */
@ActionImpl(id = "TurtleCommander.QuickAccess.9")
public class QuickAccessFavorite9Action extends QuickAccessFavoriteAction {
    public QuickAccessFavorite9Action() {
        super(9);
    }
}
