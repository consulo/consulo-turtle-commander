package consulo.turtlecommander.settings;

public enum DateUnit {
    MINUTES(60_000L, "minutes"),
    HOURS(3_600_000L, "hours"),
    DAYS(86_400_000L, "days"),
    WEEKS(604_800_000L, "weeks");

    private final long myMillisPerUnit;
    private final String myLabel;

    DateUnit(long millisPerUnit, String label) {
        myMillisPerUnit = millisPerUnit;
        myLabel = label;
    }

    public String getLabel() {
        return myLabel;
    }

    public long toMillis(long amount) {
        return amount * myMillisPerUnit;
    }
}
