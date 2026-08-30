package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Copies the file's SHA3-512 checksum. */
@ActionImpl(id = "TurtleCommander.CopyAsSha3_512")
public class CopyAsSha3_512Action extends CopyHashAction {
    public CopyAsSha3_512Action() {
        super("SHA3-512", (path, indicator) -> HashComputations.digest(path, "SHA3-512", indicator));
    }
}
