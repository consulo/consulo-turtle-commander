package consulo.turtlecommander.service;

import consulo.util.xml.serializer.annotation.AbstractCollection;
import consulo.util.xml.serializer.annotation.Tag;

import java.util.ArrayList;
import java.util.List;

@Tag("panel")
public class PanelState {
    @AbstractCollection(surroundWithTag = false)
    public List<TabState> tabs = new ArrayList<>();

    public int selectedTabIndex = 0;
}
