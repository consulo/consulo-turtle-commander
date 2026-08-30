package consulo.turtlecommander.util;

import java.util.EnumSet;
import java.util.Set;

/**
 * Raw Unix mode-bit decoding. The raw mode is preferred over
 * {@link java.nio.file.Files#getPosixFilePermissions} because the latter cannot represent the
 * setuid/setgid/sticky bits.
 */
public final class PosixModes {
    private static final int S_IFMT = 0xF000;  // 0170000 type mask
    private static final int S_IFIFO = 0x1000; // 0010000 named pipe (FIFO)

    private static final int S_ISUID = 0x800; // 04000 setuid
    private static final int S_ISGID = 0x400; // 02000 setgid
    private static final int S_ISVTX = 0x200; // 01000 sticky
    private static final int S_IRUSR = 0x100; // 0400
    private static final int S_IWUSR = 0x080; // 0200
    private static final int S_IXUSR = 0x040; // 0100
    private static final int S_IRGRP = 0x020; // 0040
    private static final int S_IWGRP = 0x010; // 0020
    private static final int S_IXGRP = 0x008; // 0010
    private static final int S_IROTH = 0x004; // 0004
    private static final int S_IWOTH = 0x002; // 0002
    private static final int S_IXOTH = 0x001; // 0001

    private PosixModes() {
    }

    public static boolean isNamedPipeMode(int mode) {
        return (mode & S_IFMT) == S_IFIFO;
    }

    /**
     * Renders a Unix file mode as the 9-character {@code ls}-style permission string, including
     * the setuid/setgid/sticky substitutions in the execute positions: {@code s}/{@code S} for
     * setuid (owner) and setgid (group), {@code t}/{@code T} for the sticky bit (others).
     * Uppercase means the special bit is set but the underlying execute bit is not.
     */
    public static String format(int mode) {
        StringBuilder sb = new StringBuilder(9);
        sb.append((mode & S_IRUSR) != 0 ? 'r' : '-');
        sb.append((mode & S_IWUSR) != 0 ? 'w' : '-');
        sb.append(specialExecChar((mode & S_IXUSR) != 0, (mode & S_ISUID) != 0, 's', 'S'));
        sb.append((mode & S_IRGRP) != 0 ? 'r' : '-');
        sb.append((mode & S_IWGRP) != 0 ? 'w' : '-');
        sb.append(specialExecChar((mode & S_IXGRP) != 0, (mode & S_ISGID) != 0, 's', 'S'));
        sb.append((mode & S_IROTH) != 0 ? 'r' : '-');
        sb.append((mode & S_IWOTH) != 0 ? 'w' : '-');
        sb.append(specialExecChar((mode & S_IXOTH) != 0, (mode & S_ISVTX) != 0, 't', 'T'));
        return sb.toString();
    }

    public static Set<PermissionFlag> toFlags(int mode) {
        Set<PermissionFlag> flags = EnumSet.noneOf(PermissionFlag.class);
        addIf(flags, mode, S_IRUSR, PermissionFlag.OWNER_READ);
        addIf(flags, mode, S_IWUSR, PermissionFlag.OWNER_WRITE);
        addIf(flags, mode, S_IXUSR, PermissionFlag.OWNER_EXECUTE);
        addIf(flags, mode, S_IRGRP, PermissionFlag.GROUP_READ);
        addIf(flags, mode, S_IWGRP, PermissionFlag.GROUP_WRITE);
        addIf(flags, mode, S_IXGRP, PermissionFlag.GROUP_EXECUTE);
        addIf(flags, mode, S_IROTH, PermissionFlag.OTHERS_READ);
        addIf(flags, mode, S_IWOTH, PermissionFlag.OTHERS_WRITE);
        addIf(flags, mode, S_IXOTH, PermissionFlag.OTHERS_EXECUTE);
        addIf(flags, mode, S_ISUID, PermissionFlag.SETUID);
        addIf(flags, mode, S_ISGID, PermissionFlag.SETGID);
        addIf(flags, mode, S_ISVTX, PermissionFlag.STICKY);
        return flags;
    }

    private static void addIf(Set<PermissionFlag> flags, int mode, int bit, PermissionFlag flag) {
        if ((mode & bit) != 0) {
            flags.add(flag);
        }
    }

    private static char specialExecChar(boolean executeSet, boolean specialSet, char onChar, char offChar) {
        if (specialSet) {
            return executeSet ? onChar : offChar;
        }
        return executeSet ? 'x' : '-';
    }
}
