package consulo.turtlecommander.settings;

public enum FileSizeFormat {
    AUTO_BINARY("Auto \u2014 1024-based (KiB, MiB, GiB)"),
    AUTO_SI("Auto \u2014 1000-based (kB, MB, GB)"),
    BYTES("Bytes (raw)");

    public static final String DEFAULT_NAME = "AUTO_BINARY";

    private final String myLabel;

    FileSizeFormat(String label) {
        myLabel = label;
    }

    public String getLabel() {
        return myLabel;
    }

    public static FileSizeFormat fromName(String name) {
        for (FileSizeFormat format : values()) {
            if (format.name().equals(name)) {
                return format;
            }
        }
        return AUTO_BINARY;
    }
}
