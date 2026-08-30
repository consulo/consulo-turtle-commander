package consulo.turtlecommander.settings;

/** How a size matcher compares an entry's size. */
public enum SizeOp {
    LT("Less than (<)"),
    LE("At most (\u2264)"),
    EQ("Equal to (=)"),
    GE("At least (\u2265)"),
    GT("Greater than (>)"),
    BETWEEN("Between");

    private final String myLabel;

    SizeOp(String label) {
        myLabel = label;
    }

    public String getLabel() {
        return myLabel;
    }
}
