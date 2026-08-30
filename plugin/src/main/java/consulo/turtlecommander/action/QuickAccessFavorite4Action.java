package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Jumps to favourite slot 4. */
@ActionImpl(id = "TurtleCommander.QuickAccess.4")
public class QuickAccessFavorite4Action extends QuickAccessFavoriteAction {
    public QuickAccessFavorite4Action() {
        super(4);
    }
}
