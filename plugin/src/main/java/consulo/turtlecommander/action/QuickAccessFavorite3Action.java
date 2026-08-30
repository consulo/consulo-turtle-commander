package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Jumps to favourite slot 3. */
@ActionImpl(id = "TurtleCommander.QuickAccess.3")
public class QuickAccessFavorite3Action extends QuickAccessFavoriteAction {
    public QuickAccessFavorite3Action() {
        super(3);
    }
}
