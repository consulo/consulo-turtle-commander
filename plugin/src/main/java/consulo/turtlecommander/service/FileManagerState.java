package consulo.turtlecommander.service;

import consulo.util.xml.serializer.annotation.AbstractCollection;
import consulo.util.xml.serializer.annotation.Tag;

import java.util.ArrayList;
import java.util.List;

/** Everything the tool window persists per project. */
public class FileManagerState {
    public float splitProportion = 0.5f;

    @Tag("leftPanel")
    public PanelState leftPanel = new PanelState();

    @Tag("rightPanel")
    public PanelState rightPanel = new PanelState();

    // FavoriteEntry carries @Tag("favorite"), so its own binding supplies the element name.
    @AbstractCollection(surroundWithTag = false)
    public List<FavoriteEntry> favoriteEntries = new ArrayList<>();

    /**
     * Most-recent-last history of commands run from the command line (Ctrl+E).
     *
     * <p>An unwrapped collection of a type with no bean binding — {@code String} here — has nothing
     * to derive an element name from, so the serializer rejects the default {@code "option"} tag.
     * Naming the element explicitly also keeps these distinct from the sibling {@code <favorite>}
     * entries, since neither collection is wrapped in one of its own.
     */
    @AbstractCollection(surroundWithTag = false, elementTypes = String.class, elementTag = "command")
    public List<String> commandHistory = new ArrayList<>();
}
