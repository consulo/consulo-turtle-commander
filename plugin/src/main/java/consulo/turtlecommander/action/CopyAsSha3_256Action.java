package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Copies the file's SHA3-256 checksum. */
@ActionImpl(id = "TurtleCommander.CopyAsSha3_256")
public class CopyAsSha3_256Action extends CopyHashAction {
    public CopyAsSha3_256Action() {
        super("SHA3-256", (path, indicator) -> HashComputations.digest(path, "SHA3-256", indicator));
    }
}
