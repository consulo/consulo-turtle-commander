package consulo.turtlecommander.util;

import java.util.List;

/**
 * OS-agnostic bag of file-permission flags used by the search dialog's permissions filter.
 * POSIX flags are only populated on Linux/macOS and DOS flags only on Windows — the UI picks
 * which subset to show based on the host OS, so a single criterion set can travel through the
 * search pipeline without branching at every call site.
 */
public enum PermissionFlag {
    OWNER_READ, OWNER_WRITE, OWNER_EXECUTE,
    GROUP_READ, GROUP_WRITE, GROUP_EXECUTE,
    OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE,
    SETUID, SETGID, STICKY,
    DOS_READ_ONLY, DOS_HIDDEN, DOS_SYSTEM, DOS_ARCHIVE;

    public static final List<PermissionFlag> POSIX_FLAGS = List.of(
        OWNER_READ, OWNER_WRITE, OWNER_EXECUTE,
        GROUP_READ, GROUP_WRITE, GROUP_EXECUTE,
        OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE,
        SETUID, SETGID, STICKY
    );

    public static final List<PermissionFlag> DOS_FLAGS = List.of(
        DOS_READ_ONLY, DOS_HIDDEN, DOS_SYSTEM, DOS_ARCHIVE
    );
}
