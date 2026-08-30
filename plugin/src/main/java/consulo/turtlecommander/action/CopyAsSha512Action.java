package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Copies the file's SHA-512 checksum. */
@ActionImpl(id = "TurtleCommander.CopyAsSha512")
public class CopyAsSha512Action extends CopyHashAction {
    public CopyAsSha512Action() {
        super("SHA-512", (path, indicator) -> HashComputations.digest(path, "SHA-512", indicator));
    }
}
