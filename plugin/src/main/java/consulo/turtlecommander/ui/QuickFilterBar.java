package consulo.turtlecommander.ui;

import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.Component;
import consulo.ui.HasValidator;
import consulo.ui.ImageBox;
import consulo.ui.Space;
import consulo.ui.TextBox;
import consulo.ui.TextBoxStyle;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.event.details.KeyCode;
import consulo.ui.layout.DockLayout;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Shared quick-filter bar (Ctrl-S) used by the file tab and the search results panel: a search box with a find icon
 * and a clear button. Starts hidden; the owner shows it via {@link #activate()} and decides what filtering means
 * through the callbacks.
 *
 * <p>Esc hides, Up/Down move the owner's selection, Enter moves focus to the owner's view with the filter still
 * applied.
 */
public class QuickFilterBar {
    private final TextBox myField = TextBox.create();
    private final DockLayout myRoot = DockLayout.create();
    private boolean myInvalid;

    /**
     * @param onFilterChanged fired on every text change with the current (untrimmed) text
     * @param onHideRequested Esc: the owner clears the filter, hides the bar and refocuses its view
     * @param onMoveSelection Up/Down delegated from the field; the offset is +1 / -1
     * @param onCommit        Enter: the owner moves focus to its view, keeping the filter applied
     */
    @RequiredUIAccess
    public QuickFilterBar(Consumer<String> onFilterChanged, Runnable onHideRequested,
                          IntConsumer onMoveSelection, Runnable onCommit) {
        myField.addStyle(TextBoxStyle.HAS_CLEAR_BUTTON);
        myField.setPrefixComponent(ImageBox.create(PlatformIconGroup.actionsSearch()));
        myField.setPlaceholder(LocalizeValue.localizeTODO("Filter, * and ? as wildcards"));

        myField.addKeyPressedListener(event -> {
            KeyCode keyCode = event.getInputDetails().getKeyCode();
            if (keyCode == null) {
                return;
            }
            int key = keyCode.key();
            if (key == KeyCode.ESCAPE.key()) {
                onHideRequested.run();
            }
            else if (key == KeyCode.UP.key() || key == KeyCode.DOWN.key()) {
                onMoveSelection.accept(key == KeyCode.DOWN.key() ? 1 : -1);
            }
            else if (key == KeyCode.ENTER.key()) {
                onCommit.run();
            }
        });
        myField.addValueListener(event -> onFilterChanged.accept(getText()));

        // a glob which does not parse is flagged the way any invalid input is
        myField.addValidator(value -> myInvalid ? new HasValidator.ValidationInfo("Invalid pattern") : null);

        myRoot.center(myField);
        myRoot.paddingBuilder().verticalSet(Space.X_SMALL).horizontalSet(Space.SMALL).apply();
        myRoot.setVisible(false);
    }

    public Component getComponent() {
        return myRoot;
    }

    public boolean isVisible() {
        return myRoot.isVisible();
    }

    public String getText() {
        String text = myField.getValue();
        return text == null ? "" : text;
    }

    /** Setting the text fires the filter callback, like typing does. */
    @RequiredUIAccess
    public void setText(String text) {
        myField.setValue(text);
    }

    /**
     * Shows the bar empty and focuses the field, or just refocuses when already visible.
     */
    @RequiredUIAccess
    public void activate() {
        if (!myRoot.isVisible()) {
            myRoot.setVisible(true);
            myField.setValue("");
        }
        myField.focus();
    }

    /**
     * Clears the text and hides the bar. Clearing a non-empty field fires the filter callback; owners that must
     * re-apply unconditionally (empty to empty fires nothing) call their filter routine explicitly afterwards.
     */
    @RequiredUIAccess
    public void deactivate() {
        myField.setValue("");
        myRoot.setVisible(false);
    }

    public boolean hasFieldFocus() {
        return myField.hasFocus();
    }

    /** Flags the text as an invalid pattern, or clears the flag. */
    @RequiredUIAccess
    public void setError(boolean error) {
        if (myInvalid != error) {
            myInvalid = error;
            myField.validate();
        }
    }
}
