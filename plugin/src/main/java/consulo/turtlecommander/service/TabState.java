package consulo.turtlecommander.service;

import consulo.util.xml.serializer.annotation.Attribute;
import consulo.util.xml.serializer.annotation.Tag;

/** One persisted tab: where it points and how it was displayed. */
@Tag("tab")
public class TabState {
    @Attribute
    public String path = "";
    @Attribute
    public String viewMode = "TABLE";

    public String columnWidths = "";
    public String columnOrder = "";
    public int sortColumn = -1;
    public boolean sortAscending = true;

    @Attribute
    public boolean showAllNestedFiles = false;

    /** Required by the XML deserializer. */
    public TabState() {
    }

    public TabState(String path, String viewMode) {
        this.path = path;
        this.viewMode = viewMode;
    }
}
