package consulo.turtlecommander.ui;

import consulo.ui.ex.awt.SearchTextField;
import consulo.ui.ex.awt.event.DocumentAdapter;
import org.jspecify.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Shared quick-filter bar (Ctrl-S) used by the file tab and the search results panel: a platform
 * {@link SearchTextField} with the standard look, embedded find icon and inline clear button.
 * Starts hidden; the owner shows it via {@link #activate()} and decides what filtering means
 * through the callbacks.
 *
 * <p>Key handling matches the hand-built bars it replaced: Esc hides, Up/Down move the owner's
 * selection without leaving the field, Enter moves focus to the owner's view with the filter still
 * applied.
 */
public class QuickFilterBar extends JPanel {
    private static final String OUTLINE_PROPERTY = "JComponent.outline";

    private final SearchTextField mySearchField = new SearchTextField(false);

    /**
     * @param onFilterChanged fired on every text change with the current (untrimmed) text
     * @param onHideRequested Esc: the owner clears the filter, hides the bar and refocuses its view
     * @param onMoveSelection Up/Down delegated from the field; the offset is +1 / -1
     * @param onCommit        Enter: the owner moves focus to its view, keeping the filter applied
     */
    public QuickFilterBar(Consumer<String> onFilterChanged, Runnable onHideRequested,
                          IntConsumer onMoveSelection, Runnable onCommit) {
        super(new BorderLayout(4, 0));

        setVisible(false);
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(mySearchField, BorderLayout.CENTER);

        mySearchField.getTextEditor().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE -> {
                        onHideRequested.run();
                        e.consume();
                    }
                    case KeyEvent.VK_UP, KeyEvent.VK_DOWN -> {
                        onMoveSelection.accept(e.getKeyCode() == KeyEvent.VK_DOWN ? 1 : -1);
                        e.consume();
                    }
                    case KeyEvent.VK_ENTER -> {
                        onCommit.run();
                        e.consume();
                    }
                    default -> {
                        // Everything else edits the filter text normally.
                    }
                }
            }
        });

        mySearchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                onFilterChanged.accept(mySearchField.getText());
            }
        });
    }

    public String getText() {
        return mySearchField.getText();
    }

    /** Setting the text fires the filter callback through the document listener, like typing does. */
    public void setText(String text) {
        mySearchField.setText(text);
    }

    /**
     * Shows the bar empty and focuses the field, or just refocuses when already visible. The owner
     * revalidates its own layout afterwards.
     */
    public void activate() {
        if (isVisible()) {
            mySearchField.getTextEditor().requestFocusInWindow();
            return;
        }
        setVisible(true);
        mySearchField.setText("");
        mySearchField.getTextEditor().requestFocusInWindow();
    }

    /**
     * Clears the text and hides the bar. Clearing a non-empty field fires the filter callback;
     * owners that must re-apply unconditionally (empty→empty fires nothing) call their filter
     * routine explicitly afterwards.
     */
    public void deactivate() {
        mySearchField.setText("");
        setVisible(false);
    }

    public boolean hasFieldFocus() {
        return mySearchField.getTextEditor().hasFocus();
    }

    /**
     * Toggles the platform error outline — the standard way to flag invalid input (here, a glob
     * that doesn't parse) instead of hand-tinting colours the LAF can override during paint.
     */
    public void setError(boolean error) {
        @Nullable String newValue = error ? "error" : null;
        if (!java.util.Objects.equals(mySearchField.getTextEditor().getClientProperty(OUTLINE_PROPERTY), newValue)) {
            mySearchField.getTextEditor().putClientProperty(OUTLINE_PROPERTY, newValue);
            mySearchField.getTextEditor().repaint();
        }
    }
}
