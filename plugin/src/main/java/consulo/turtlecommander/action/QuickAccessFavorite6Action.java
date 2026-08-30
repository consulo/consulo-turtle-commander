package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Jumps to favourite slot 6. */
@ActionImpl(id = "TurtleCommander.QuickAccess.6")
public class QuickAccessFavorite6Action extends QuickAccessFavoriteAction {
    public QuickAccessFavorite6Action() {
        super(6);
    }
}
