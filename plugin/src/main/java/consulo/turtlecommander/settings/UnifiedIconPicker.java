package consulo.turtlecommander.settings;

import consulo.localize.LocalizeValue;
import consulo.ui.Button;
import consulo.ui.ButtonStyle;
import consulo.ui.Component;
import consulo.ui.Menu;
import consulo.ui.MenuItem;
import consulo.ui.PopupMenu;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** A button showing a rule icon, which opens the icons by category; the empty id is "None", the file-type icon. */
public final class UnifiedIconPicker {
    private static final LocalizeValue NO_ICON_TEXT = LocalizeValue.of("...");

    private final Button myButton = Button.create(NO_ICON_TEXT);
    private String myIconId = "";
    private @Nullable Runnable myOnChange;

    @RequiredUIAccess
    public UnifiedIconPicker() {
        myButton.addStyle(ButtonStyle.BORDERLESS);
        myButton.addClickListener(event -> showIconChooserPopup(myButton, this::setIconId));
        setIconId("");
    }

    public Component getComponent() {
        return myButton;
    }

    public void setOnChange(@Nullable Runnable onChange) {
        myOnChange = onChange;
    }

    @RequiredUIAccess
    public void setIconId(String id) {
        myIconId = id;
        Image icon = RuleIcons.resolve(id);
        myButton.setIcon(icon);
        myButton.setText(icon != null ? LocalizeValue.empty() : NO_ICON_TEXT);
        String label = RuleIcons.label(id);
        myButton.setToolTipText(LocalizeValue.of(id.isEmpty() ? "Default (file-type icon)" : label != null ? label : id));
        Runnable onChange = myOnChange;
        if (onChange != null) {
            onChange.run();
        }
    }

    public String getIconId() {
        return myIconId;
    }

    /** "None" and a submenu per category, under {@code anchor}; {@code onPick} receives the key, empty for "None". */
    @RequiredUIAccess
    public static void showIconChooserPopup(Component anchor, Consumer<String> onPick) {
        Map<String, List<RuleIcons.Entry>> byCategory = new LinkedHashMap<>();
        for (RuleIcons.Entry entry : RuleIcons.ENTRIES) {
            byCategory.computeIfAbsent(entry.category(), key -> new ArrayList<>()).add(entry);
        }

        PopupMenu menu = PopupMenu.create(anchor);
        MenuItem none = MenuItem.create(LocalizeValue.localizeTODO("None"));
        none.addClickListener(event -> onPick.accept(""));
        menu.add(none);
        for (Map.Entry<String, List<RuleIcons.Entry>> category : byCategory.entrySet()) {
            Menu submenu = Menu.create(LocalizeValue.of(category.getKey()));
            for (RuleIcons.Entry entry : category.getValue()) {
                MenuItem item = MenuItem.create(LocalizeValue.of(entry.label()));
                item.setIcon(entry.icon());
                item.addClickListener(event -> onPick.accept(entry.key()));
                submenu.add(item);
            }
            menu.add(submenu);
        }
        menu.show(0, 0);
    }
}
