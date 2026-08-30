package consulo.turtlecommander.service;

import consulo.util.xml.serializer.annotation.Attribute;
import consulo.util.xml.serializer.annotation.Tag;

@Tag("favorite")
public class FavoriteEntry {
    @Attribute
    public String path = "";
    @Attribute
    public String color = "";

    /** Stable {@code RuleIcons} key, or empty for the default folder icon. */
    @Attribute
    public String icon = "";

    /** Required by the XML deserializer. */
    public FavoriteEntry() {
    }

    public FavoriteEntry(String path, String color, String icon) {
        this.path = path;
        this.color = color;
        this.icon = icon;
    }
}
