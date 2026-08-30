package consulo.turtlecommander.settings;

/** How a date matcher compares a timestamp. */
public enum DateOp {
    BEFORE("Before"),
    AFTER("On or after"),
    BETWEEN("Between"),
    WITHIN_LAST("Within last"),
    OLDER_THAN("Older than");

    private final String myLabel;

    DateOp(String label) {
        myLabel = label;
    }

    public String getLabel() {
        return myLabel;
    }
}
