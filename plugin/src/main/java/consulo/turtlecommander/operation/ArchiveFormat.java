package consulo.turtlecommander.operation;

/** The archive containers the Pack operation can write. */
public enum ArchiveFormat {
    ZIP(".zip", ".zip"),
    TAR_GZ(".tar.gz", ".tar.gz");

    private final String myExtension;
    private final String myLabel;

    ArchiveFormat(String extension, String label) {
        myExtension = extension;
        myLabel = label;
    }

    public String extension() {
        return myExtension;
    }

    public String label() {
        return myLabel;
    }
}
