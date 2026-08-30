package consulo.turtlecommander.settings;

/** Which entry kinds a name matcher considers. */
public enum AppliesTo {
    FILE("Files only"),
    DIR("Directories only"),
    BOTH("Files and directories");

    private final String myLabel;

    AppliesTo(String label) {
        myLabel = label;
    }

    public String getLabel() {
        return myLabel;
    }
}
