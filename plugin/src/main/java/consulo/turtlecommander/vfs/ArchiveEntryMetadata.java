package consulo.turtlecommander.vfs;

/**
 * Owner / group / permission strings recorded inside an archive for one entry. Fields are empty
 * when the format doesn't carry them (e.g. zip stores a Unix mode but no owner/group names), so
 * consumers treat empty as "unknown / inherit" rather than a real value.
 */
public record ArchiveEntryMetadata(String owner, String group, String permissions) {
    public static final ArchiveEntryMetadata EMPTY = new ArchiveEntryMetadata("", "", "");

    public static ArchiveEntryMetadata ofPermissions(String permissions) {
        return new ArchiveEntryMetadata("", "", permissions);
    }
}
