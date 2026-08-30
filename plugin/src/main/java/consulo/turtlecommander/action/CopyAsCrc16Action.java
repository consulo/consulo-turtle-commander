package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;

/** Copies the file's CRC16 checksum. */
@ActionImpl(id = "TurtleCommander.CopyAsCrc16")
public class CopyAsCrc16Action extends CopyHashAction {
    public CopyAsCrc16Action() {
        super("CRC16", HashComputations::crc16);
    }
}
