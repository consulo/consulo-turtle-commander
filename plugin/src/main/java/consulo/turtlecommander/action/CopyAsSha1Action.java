package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Copies the file's SHA-1 checksum. */
@ActionImpl(id = "TurtleCommander.CopyAsSha1")
public class CopyAsSha1Action extends CopyHashAction {
    public CopyAsSha1Action() {
        super("SHA-1", (path, indicator) -> HashComputations.digest(path, "SHA-1", indicator));
    }
}
