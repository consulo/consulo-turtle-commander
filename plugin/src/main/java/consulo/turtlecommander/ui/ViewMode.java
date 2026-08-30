package consulo.turtlecommander.ui;

/** The four ways a panel can present a directory. */
public enum ViewMode {
    TABLE, LIST, THUMBNAIL, TREE;

    public static ViewMode fromName(String name) {
        for (ViewMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return TABLE;
    }
}
