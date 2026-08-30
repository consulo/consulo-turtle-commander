package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Copies the file's MD5 checksum. */
@ActionImpl(id = "TurtleCommander.CopyAsMd5")
public class CopyAsMd5Action extends CopyHashAction {
    public CopyAsMd5Action() {
        super("MD5", (path, indicator) -> HashComputations.digest(path, "MD5", indicator));
    }
}
