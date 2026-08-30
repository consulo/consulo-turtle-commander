package consulo.turtlecommander.settings;

import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Curated set of platform icons offered as per-rule icon overrides in the colorization rules
 * editor.
 *
 * <p>Rules persist the {@link Entry#key()} — a stable string we own — never a platform icon
 * resource path. That decouples saved settings and exported {@code .tcrules} files from IDE
 * internals: an unknown key simply resolves to null and the entry falls back to its default
 * file-type/folder icon, rather than crashing or showing a broken image. Keys are therefore an
 * append-only contract: add freely, rename with care.
 *
 * <p>The IntelliJ build offered a few icons that Consulo's icon library has no counterpart for
 * ({@code upload}, {@code user}, {@code web}, {@code console}, and the Java/JavaScript/JSON/CSS/YAML
 * file types). Those keys are simply absent here; by the fallback contract above, rules importing
 * them degrade to the default icon.
 */
public final class RuleIcons {
    public record Entry(String key, String label, String category, Image icon) {
    }

    public static final List<Entry> ENTRIES = List.of(
        // Marks & status
        new Entry("star", "Star", "Marks", PlatformIconGroup.nodesFavorite()),
        new Entry("check", "Checkmark", "Marks", PlatformIconGroup.actionsChecked()),
        new Entry("ok", "Inspections OK", "Marks", PlatformIconGroup.generalInspectionsok()),
        new Entry("add", "Plus", "Marks", PlatformIconGroup.generalAdd()),
        new Entry("remove", "Minus", "Marks", PlatformIconGroup.generalRemove()),
        new Entry("error", "Error", "Marks", PlatformIconGroup.generalError()),
        new Entry("warning", "Warning", "Marks", PlatformIconGroup.generalWarning()),
        new Entry("info", "Info", "Marks", PlatformIconGroup.generalInformation()),
        new Entry("todo", "To-do", "Marks", PlatformIconGroup.generalTododefault()),
        new Entry("todo-important", "To-do (important)", "Marks", PlatformIconGroup.generalTodoimportant()),
        new Entry("bulb", "Idea", "Marks", PlatformIconGroup.actionsIntentionbulb()),
        new Entry("filter", "Filter", "Marks", PlatformIconGroup.generalFilter()),
        new Entry("settings", "Settings", "Marks", PlatformIconGroup.generalSettings()),
        new Entry("pin", "Pin", "Marks", PlatformIconGroup.generalPin_tab()),

        // Actions
        new Entry("run", "Run", "Actions", PlatformIconGroup.actionsExecute()),
        new Entry("stop", "Stop", "Actions", PlatformIconGroup.actionsSuspend()),
        new Entry("restart", "Restart", "Actions", PlatformIconGroup.actionsRestart()),
        new Entry("refresh", "Refresh", "Actions", PlatformIconGroup.actionsRefresh()),
        new Entry("find", "Find", "Actions", PlatformIconGroup.actionsFind()),
        new Entry("edit", "Edit", "Actions", PlatformIconGroup.actionsEdit()),
        new Entry("copy", "Copy", "Actions", PlatformIconGroup.actionsCopy()),
        new Entry("annotate", "Annotate", "Actions", PlatformIconGroup.actionsAnnotate()),
        new Entry("lightning", "Lightning", "Actions", PlatformIconGroup.actionsLightning()),
        new Entry("trash", "Trash", "Actions", PlatformIconGroup.actionsGc()),
        new Entry("cancel", "Cancel", "Actions", PlatformIconGroup.actionsCancel()),
        new Entry("close", "Close", "Actions", PlatformIconGroup.actionsClose()),
        new Entry("download", "Download", "Actions", PlatformIconGroup.actionsDownload()),
        new Entry("back", "Back", "Actions", PlatformIconGroup.actionsBack()),
        new Entry("forward", "Forward", "Actions", PlatformIconGroup.actionsForward()),

        // Nodes
        new Entry("folder", "Folder", "Nodes", PlatformIconGroup.nodesFolder()),
        new Entry("module", "Module", "Nodes", PlatformIconGroup.nodesModule()),
        new Entry("plugin", "Plugin", "Nodes", PlatformIconGroup.nodesPlugin()),
        new Entry("home", "Home", "Nodes", PlatformIconGroup.nodesHomefolder()),
        new Entry("class", "Class", "Nodes", PlatformIconGroup.nodesClass()),
        new Entry("interface", "Interface", "Nodes", PlatformIconGroup.nodesInterface()),
        new Entry("enum", "Enum", "Nodes", PlatformIconGroup.nodesEnum()),
        new Entry("method", "Method", "Nodes", PlatformIconGroup.nodesMethod()),
        new Entry("field", "Field", "Nodes", PlatformIconGroup.nodesField()),
        new Entry("function", "Function", "Nodes", PlatformIconGroup.nodesFunction()),
        new Entry("variable", "Variable", "Nodes", PlatformIconGroup.nodesVariable()),
        new Entry("constant", "Constant", "Nodes", PlatformIconGroup.nodesConstant()),
        new Entry("property", "Property", "Nodes", PlatformIconGroup.nodesProperty()),
        new Entry("package", "Package", "Nodes", PlatformIconGroup.nodesPackage()),
        new Entry("tag", "Tag", "Nodes", PlatformIconGroup.nodesTag()),

        // File types
        new Entry("text", "Text", "File types", PlatformIconGroup.filetypesText()),
        new Entry("archive", "Archive", "File types", PlatformIconGroup.filetypesArchive()),
        new Entry("xml", "XML", "File types", PlatformIconGroup.filetypesXml()),
        new Entry("html", "HTML", "File types", PlatformIconGroup.filetypesHtml()),
        new Entry("config", "Config", "File types", PlatformIconGroup.filetypesConfig()),
        new Entry("any", "Generic file", "File types", PlatformIconGroup.filetypesAny_type()),

        // Version control
        new Entry("branch", "Branch", "Version control", PlatformIconGroup.vcsBranch()),
        new Entry("history", "History", "Version control", PlatformIconGroup.vcsHistory())
    );

    private static final Map<String, Entry> BY_KEY = buildIndex();

    private RuleIcons() {
    }

    /** The icon for {@code key}, or null for the empty key or any key not in the current set. */
    public static @Nullable Image resolve(String key) {
        if (key.isEmpty()) {
            return null;
        }
        Entry entry = BY_KEY.get(key);
        return entry != null ? entry.icon() : null;
    }

    /** Human-readable label for {@code key}, or null if unknown. */
    public static @Nullable String label(String key) {
        Entry entry = BY_KEY.get(key);
        return entry != null ? entry.label() : null;
    }

    private static Map<String, Entry> buildIndex() {
        Map<String, Entry> index = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            index.put(entry.key(), entry);
        }
        return index;
    }
}
