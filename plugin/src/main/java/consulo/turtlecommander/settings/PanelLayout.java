package consulo.turtlecommander.settings;

public enum PanelLayout {
    HORIZONTAL,
    VERTICAL,
    SINGLE;

    public static PanelLayout fromName(String name) {
        for (PanelLayout layout : values()) {
            if (layout.name().equals(name)) {
                return layout;
            }
        }
        return HORIZONTAL;
    }
}
