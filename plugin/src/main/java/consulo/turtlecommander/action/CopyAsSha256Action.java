package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Copies the file's SHA-256 checksum. */
@ActionImpl(id = "TurtleCommander.CopyAsSha256")
public class CopyAsSha256Action extends CopyHashAction {
    public CopyAsSha256Action() {
        super("SHA-256", (path, indicator) -> HashComputations.digest(path, "SHA-256", indicator));
    }
}
