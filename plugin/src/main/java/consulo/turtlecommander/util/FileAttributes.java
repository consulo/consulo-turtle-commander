package consulo.turtlecommander.util;

import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

/**
 * Per-file metadata reads used to populate {@link consulo.turtlecommander.model.FileEntry}.
 * Every accessor degrades to an empty/null value rather than throwing: an entry we cannot stat
 * is still worth listing, just without the column value.
 */
public final class FileAttributes {
    private FileAttributes() {
    }

    public static String readOwner(Path path) {
        try {
            return Files.getOwner(path).getName();
        }
        catch (Exception e) {
            return "";
        }
    }

    public static String readGroup(Path path) {
        try {
            PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
            if (view == null) {
                return "";
            }
            PosixFileAttributes attributes = view.readAttributes();
            return attributes.group().getName();
        }
        catch (Exception e) {
            return "";
        }
    }

    /**
     * Unix inode number ({@code unix:ino}); null on filesystems that don't expose it.
     */
    public static @Nullable Long readInode(Path path) {
        return readUnixAttribute(path, "unix:ino", Long.class);
    }

    /**
     * Hard-link count ({@code unix:nlink}); null on filesystems that don't expose it.
     */
    public static @Nullable Integer readNlink(Path path) {
        return readUnixAttribute(path, "unix:nlink", Integer.class);
    }

    public static boolean isNamedPipe(Path path) {
        Integer mode = readUnixAttribute(path, "unix:mode", Integer.class);
        return mode != null && PosixModes.isNamedPipeMode(mode);
    }

    public static String readPermissions(Path path, boolean windows) {
        try {
            if (windows) {
                DosFileAttributes attributes = Files.readAttributes(path, DosFileAttributes.class);
                StringBuilder sb = new StringBuilder(4);
                if (attributes.isReadOnly()) {
                    sb.append('R');
                }
                if (attributes.isHidden()) {
                    sb.append('H');
                }
                if (attributes.isSystem()) {
                    sb.append('S');
                }
                if (attributes.isArchive()) {
                    sb.append('A');
                }
                return sb.toString();
            }

            // Prefer the raw mode so setuid/setgid/sticky show up in ls style (e.g. `rwsr-xr-t`).
            // The `unix` view is available on Linux and macOS; if it isn't, fall back to the
            // standard 9-bit set, which renders the same rwx string minus the special bits.
            Integer mode = readUnixAttribute(path, "unix:mode", Integer.class);
            if (mode != null) {
                return PosixModes.format(mode);
            }
            return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
        }
        catch (Exception e) {
            return "";
        }
    }

    public static Set<PermissionFlag> readPermissionFlags(Path path, boolean windows) {
        try {
            if (windows) {
                DosFileAttributes attributes = Files.readAttributes(path, DosFileAttributes.class);
                Set<PermissionFlag> flags = EnumSet.noneOf(PermissionFlag.class);
                if (attributes.isReadOnly()) {
                    flags.add(PermissionFlag.DOS_READ_ONLY);
                }
                if (attributes.isHidden()) {
                    flags.add(PermissionFlag.DOS_HIDDEN);
                }
                if (attributes.isSystem()) {
                    flags.add(PermissionFlag.DOS_SYSTEM);
                }
                if (attributes.isArchive()) {
                    flags.add(PermissionFlag.DOS_ARCHIVE);
                }
                return flags;
            }

            Integer mode = readUnixAttribute(path, "unix:mode", Integer.class);
            if (mode != null) {
                return PosixModes.toFlags(mode);
            }
            return toFlags(Files.getPosixFilePermissions(path));
        }
        catch (Exception e) {
            return Set.of();
        }
    }

    private static Set<PermissionFlag> toFlags(Set<PosixFilePermission> permissions) {
        Set<PermissionFlag> flags = EnumSet.noneOf(PermissionFlag.class);
        for (PosixFilePermission permission : permissions) {
            flags.add(switch (permission) {
                case OWNER_READ -> PermissionFlag.OWNER_READ;
                case OWNER_WRITE -> PermissionFlag.OWNER_WRITE;
                case OWNER_EXECUTE -> PermissionFlag.OWNER_EXECUTE;
                case GROUP_READ -> PermissionFlag.GROUP_READ;
                case GROUP_WRITE -> PermissionFlag.GROUP_WRITE;
                case GROUP_EXECUTE -> PermissionFlag.GROUP_EXECUTE;
                case OTHERS_READ -> PermissionFlag.OTHERS_READ;
                case OTHERS_WRITE -> PermissionFlag.OTHERS_WRITE;
                case OTHERS_EXECUTE -> PermissionFlag.OTHERS_EXECUTE;
            });
        }
        return flags;
    }

    private static <T> @Nullable T readUnixAttribute(Path path, String attribute, Class<T> type) {
        try {
            Object value = Files.getAttribute(path, attribute);
            return type.isInstance(value) ? type.cast(value) : null;
        }
        catch (Exception e) {
            return null;
        }
    }
}
