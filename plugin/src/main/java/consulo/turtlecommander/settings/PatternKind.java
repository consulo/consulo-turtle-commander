package consulo.turtlecommander.settings;

/** How a text pattern is interpreted. */
public enum PatternKind {
    EXACT("Exact match"),
    GLOB("Glob pattern"),
    REGEX("Regular expression");

    private final String myLabel;

    PatternKind(String label) {
        myLabel = label;
    }

    public String getLabel() {
        return myLabel;
    }
}
