package consulo.turtlecommander.settings;

/** Which symbolic links a matcher accepts. */
public enum SymlinkState {
    ANY("Any symlink"),
    VALID("Valid (target exists)"),
    BROKEN("Broken (target missing)");

    private final String myLabel;

    SymlinkState(String label) {
        myLabel = label;
    }

    public String getLabel() {
        return myLabel;
    }
}
