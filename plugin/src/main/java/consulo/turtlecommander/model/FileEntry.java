package consulo.turtlecommander.model;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * One row in a file panel.
 *
 * @param inode          filesystem inode number ({@code unix:ino}); null on Windows or when not read
 * @param nlink          hard-link count ({@code unix:nlink}); null on Windows or when not read
 * @param brokenSymlink  a symlink whose target cannot be resolved
 * @param linkTarget     the raw link target for display when {@code symbolicLink}; null otherwise
 * @param namedPipe      a Unix FIFO; always false on Windows
 */
public record FileEntry(
    String name,
    Path path,
    boolean directory,
    long size,
    @Nullable FileTime creationTime,
    @Nullable FileTime lastModified,
    String owner,
    String group,
    String permissions,
    @Nullable Long inode,
    @Nullable Integer nlink,
    boolean parentLink,
    boolean symbolicLink,
    boolean brokenSymlink,
    @Nullable String linkTarget,
    boolean namedPipe
) {
    public static Builder builder(String name, Path path) {
        return new Builder(name, path);
    }

    public static final class Builder {
        private final String myName;
        private final Path myPath;
        private boolean myDirectory;
        private long mySize;
        private @Nullable FileTime myCreationTime;
        private @Nullable FileTime myLastModified;
        private String myOwner = "";
        private String myGroup = "";
        private String myPermissions = "";
        private @Nullable Long myInode;
        private @Nullable Integer myNlink;
        private boolean myParentLink;
        private boolean mySymbolicLink;
        private boolean myBrokenSymlink;
        private @Nullable String myLinkTarget;
        private boolean myNamedPipe;

        private Builder(String name, Path path) {
            myName = name;
            myPath = path;
        }

        public Builder directory(boolean directory) {
            myDirectory = directory;
            return this;
        }

        public Builder size(long size) {
            mySize = size;
            return this;
        }

        public Builder creationTime(@Nullable FileTime creationTime) {
            myCreationTime = creationTime;
            return this;
        }

        public Builder lastModified(@Nullable FileTime lastModified) {
            myLastModified = lastModified;
            return this;
        }

        public Builder owner(String owner) {
            myOwner = owner;
            return this;
        }

        public Builder group(String group) {
            myGroup = group;
            return this;
        }

        public Builder permissions(String permissions) {
            myPermissions = permissions;
            return this;
        }

        public Builder inode(@Nullable Long inode) {
            myInode = inode;
            return this;
        }

        public Builder nlink(@Nullable Integer nlink) {
            myNlink = nlink;
            return this;
        }

        public Builder parentLink(boolean parentLink) {
            myParentLink = parentLink;
            return this;
        }

        public Builder symbolicLink(boolean symbolicLink) {
            mySymbolicLink = symbolicLink;
            return this;
        }

        public Builder brokenSymlink(boolean brokenSymlink) {
            myBrokenSymlink = brokenSymlink;
            return this;
        }

        public Builder linkTarget(@Nullable String linkTarget) {
            myLinkTarget = linkTarget;
            return this;
        }

        public Builder namedPipe(boolean namedPipe) {
            myNamedPipe = namedPipe;
            return this;
        }

        public FileEntry build() {
            return new FileEntry(myName, myPath, myDirectory, mySize, myCreationTime, myLastModified, myOwner, myGroup,
                myPermissions, myInode, myNlink, myParentLink, mySymbolicLink, myBrokenSymlink, myLinkTarget, myNamedPipe);
        }
    }
}
