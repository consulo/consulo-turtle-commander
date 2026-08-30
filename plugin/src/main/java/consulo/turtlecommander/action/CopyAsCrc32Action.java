package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Copies the file's CRC32 checksum. */
@ActionImpl(id = "TurtleCommander.CopyAsCrc32")
public class CopyAsCrc32Action extends CopyHashAction {
    public CopyAsCrc32Action() {
        super("CRC32", HashComputations::crc32);
    }
}
