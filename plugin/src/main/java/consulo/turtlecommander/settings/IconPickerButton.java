package consulo.turtlecommander.settings;

import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.PopupStep;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Picks a {@link RuleIcons} override, or clears it back to the file-type icon. */
public class IconPickerButton extends JButton {
    private static final int BUTTON_SIZE = 28;
    private static final String NO_ICON_TEXT = "...";

    private String myIconId = "";
    private @Nullable Runnable myOnChange;

    public IconPickerButton() {
        super(NO_ICON_TEXT);
        setPreferredSize(new Dimension(BUTTON_SIZE, BUTTON_SIZE));
        setFocusable(false);
        addActionListener(e -> showIconChooserPopup(this, this::setIconId));
    }

    public void setOnChange(@Nullable Runnable onChange) {
        myOnChange = onChange;
    }

    public void setIconId(String id) {
        myIconId = id;
        Image icon = RuleIcons.resolve(id);
        setIcon(icon != null ? TargetAWT.to(icon) : null);
        setText(icon != null ? "" : NO_ICON_TEXT);

        String label = RuleIcons.label(id);
        setToolTipText(id.isEmpty() ? "Default (file-type icon)" : label != null ? label : id);
        if (myOnChange != null) {
            myOnChange.run();
        }
    }

    public String getIconId() {
        return myIconId;
    }

    /**
     * A two-level chooser: categories, then their icons, with a leading "None" that clears the
     * override. {@code onPick} receives the entry key, empty for "None".
     */
    public static void showIconChooserPopup(JComponent anchor, Consumer<String> onPick) {
        Map<String, List<RuleIcons.Entry>> byCategory = new LinkedHashMap<>();
        for (RuleIcons.Entry entry : RuleIcons.ENTRIES) {
            byCategory.computeIfAbsent(entry.category(), key -> new ArrayList<>()).add(entry);
        }

        // A leading null stands for "None"; every other row opens its category's submenu.
        List<@Nullable String> topItems = new ArrayList<>();
        topItems.add(null);
        topItems.addAll(byCategory.keySet());

        BaseListPopupStep<String> top = new BaseListPopupStep<>("Choose Icon", topItems) {
            @Override
            public String getTextFor(@Nullable String value) {
                return value != null ? value : "None";
            }

            @Override
            public boolean hasSubstep(@Nullable String selectedValue) {
                return selectedValue != null;
            }

            @Override
            public @Nullable PopupStep<?> onChosen(@Nullable String selectedValue, boolean finalChoice) {
                if (selectedValue == null) {
                    return doFinalStep(() -> onPick.accept(""));
                }
                return new BaseListPopupStep<RuleIcons.Entry>(selectedValue,
                    byCategory.getOrDefault(selectedValue, List.of())) {
                    @Override
                    public String getTextFor(RuleIcons.Entry value) {
                        return value.label();
                    }

                    @Override
                    public Image getIconFor(RuleIcons.Entry value) {
                        return value.icon();
                    }

                    @Override
                    public boolean isSpeedSearchEnabled() {
                        return true;
                    }

                    @Override
                    public @Nullable PopupStep<?> onChosen(RuleIcons.Entry value, boolean finalChoice) {
                        return doFinalStep(() -> onPick.accept(value.key()));
                    }
                };
            }
        };

        JBPopupFactory.getInstance().createListPopup(top).showUnderneathOf(anchor);
    }
}
