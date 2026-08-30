package consulo.turtlecommander.ui;

import consulo.application.util.SystemInfo;
import consulo.project.Project;
import consulo.turtlecommander.filetype.TurtleCommanderNotifications;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.operation.CdCommand;
import consulo.turtlecommander.service.FileManagerStateService;
import consulo.turtlecommander.util.UiTask;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.TextFieldWithHistory;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The bottom command line shared by both panels, hidden until activated.
 *
 * <p>The input is an editable combo box whose drop-down lists the command history. While the editor
 * holds focus the user can keep navigating files: Tab retargets the other panel — routed through
 * {@link FileManagerStateService#switchToOtherPanel()} so focus stays in the field — Up/Down move
 * the active tab's selection, Ctrl+Up/Ctrl+Down walk the history, and Ctrl+Enter inserts the
 * selected file name. While the drop-down is open those keys drive the list instead. Enter runs the
 * command in the active panel's directory and refreshes both panels when the process finishes; Esc
 * hides the bar and returns focus to the active panel.
 */
public class CommandInputBar extends JPanel {
    /** Refresh the panels without waiting for completion once a command has run this long. */
    private static final long LONG_RUN_REFRESH_MS = 10_000L;

    /** Bare interactive shells, launched through {@code start} so they get their own window. */
    private static final Set<String> BARE_SHELLS = Set.of(
        "cmd", "cmd.exe",
        "powershell", "powershell.exe",
        "pwsh", "pwsh.exe",
        "bash", "bash.exe",
        "wsl", "wsl.exe");

    private static final int MAX_PROMPT_LENGTH = 48;
    private static final int MAX_ERROR_DETAIL = 500;

    private final Project myProject;
    private final FileManagerPanel myLeftPanel;
    private final FileManagerPanel myRightPanel;
    private final FileManagerStateService myStateService;

    private final JBLabel myPromptLabel = new JBLabel();
    private final TextFieldWithHistory myField = new TextFieldWithHistory();
    private final JTextField myEditor = myField.getTextEditor();

    private FileManagerPanel myActivePanel;

    /**
     * History browsing position: -1 when not browsing, otherwise an index into the history list
     * where {@code size()} means "back to the unsent draft". The draft preserves what the user had
     * typed before walking back, and the suppress flag keeps programmatic text changes from
     * clearing the position.
     */
    private int myHistoryIndex = -1;
    private String myDraft = "";
    private boolean mySuppressHistoryReset;

    public CommandInputBar(Project project, FileManagerPanel leftPanel, FileManagerPanel rightPanel) {
        super(new BorderLayout(6, 0));
        myProject = project;
        myLeftPanel = leftPanel;
        myRightPanel = rightPanel;
        myActivePanel = leftPanel;
        myStateService = FileManagerStateService.getInstance(project);

        setVisible(false);
        setBorder(BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(3, 6)));
        add(myPromptLabel, BorderLayout.WEST);
        add(myField, BorderLayout.CENTER);

        // The persisted history is already capped; never trim the list we set.
        myField.setHistorySize(-1);
        TextFieldContextMenuUtil.installStandardContextMenu(myEditor);
        // Tab must reach our key handler rather than moving focus out of the field.
        myEditor.setFocusTraversalKeysEnabled(false);

        myEditor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPressed(e);
            }
        });

        // Typing abandons history browsing and returns to a fresh draft.
        myEditor.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                if (!mySuppressHistoryReset) {
                    myHistoryIndex = -1;
                }
            }
        });
    }

    private void handleKeyPressed(KeyEvent e) {
        // While the drop-down is open, let the combo box own these keys.
        if (myField.isPopupVisible()) {
            return;
        }
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE -> {
                hideBar();
                e.consume();
            }
            case KeyEvent.VK_UP -> {
                if (e.isControlDown()) {
                    recallHistory(-1);
                }
                else {
                    moveActiveTabSelection(-1);
                }
                e.consume();
            }
            case KeyEvent.VK_DOWN -> {
                if (e.isControlDown()) {
                    recallHistory(1);
                }
                else {
                    moveActiveTabSelection(1);
                }
                e.consume();
            }
            case KeyEvent.VK_ENTER -> {
                if (e.isControlDown()) {
                    insertSelectedName();
                }
                else {
                    executeCommand();
                }
                e.consume();
            }
            default -> {
                // Everything else is ordinary text entry.
            }
        }
    }

    public void activate(@Nullable FileManagerPanel panel) {
        if (!isVisible()) {
            if (panel != null) {
                myActivePanel = panel;
            }
            setVisible(true);
            updatePrompt();
            revalidate();
        }
        syncHistory();
        myHistoryIndex = -1;
        myEditor.requestFocusInWindow();
        myEditor.selectAll();
    }

    public boolean hasFieldFocus() {
        return myEditor.hasFocus();
    }

    public void switchTargetPanel() {
        myActivePanel = myActivePanel == myLeftPanel ? myRightPanel : myLeftPanel;
        updatePrompt();
    }

    private void hideBar() {
        setVisible(false);
        revalidate();
        myActivePanel.focusActiveTab();
    }

    private @Nullable FileTab activeTab() {
        return myActivePanel.getActiveTab();
    }

    private void moveActiveTabSelection(int offset) {
        FileTab tab = activeTab();
        if (tab != null) {
            tab.moveSelection(offset);
        }
    }

    /** Refreshes the drop-down from the persisted history, most recent first. */
    private void syncHistory() {
        List<String> history = new ArrayList<>(myStateService.getCommandHistory());
        java.util.Collections.reverse(history);
        myField.setHistory(history);
    }

    private void updatePrompt() {
        FileTab tab = activeTab();
        String dir = tab != null ? tab.getCurrentPath().toString() : "";
        myPromptLabel.setText(StringUtil.trimMiddle(dir, MAX_PROMPT_LENGTH) + ">");
        myPromptLabel.setToolTipText(dir);
    }

    private void insertSelectedName() {
        FileTab tab = activeTab();
        FileEntry entry = tab != null ? FileTabSelectionUtil.getSelectedEntry(tab) : null;
        if (entry == null || entry.parentLink()) {
            return;
        }
        String name = entry.name();
        // Quote names containing whitespace so they reach the shell as a single argument.
        boolean needsQuoting = name.chars().anyMatch(Character::isWhitespace);
        myEditor.replaceSelection(needsQuoting ? "\"" + name + "\"" : name);
    }

    private void executeCommand() {
        String command = myEditor.getText().trim();
        if (command.isEmpty()) {
            return;
        }
        FileTab tab = activeTab();
        if (tab == null) {
            return;
        }
        if (tab.getCurrentVfs() != null) {
            TurtleCommanderNotifications.error(myProject, "Cannot run commands inside an archive");
            return;
        }

        myStateService.addCommandToHistory(command);
        syncHistory();

        // A `cd` can't outlive a one-shot child shell, so handle it by navigating the active tab
        // rather than spawning a process that would immediately discard the change.
        String cdTarget = CdCommand.parseArgument(command);
        if (cdTarget != null) {
            FileTabNavigationUtil.changeDirectoryFromCommand(tab, cdTarget, this::updatePrompt);
        }
        else {
            runCommand(command, tab.getCurrentPath());
        }
        myEditor.setText("");
        myHistoryIndex = -1;
    }

    /** Walks the command history: {@code delta} is -1 for older, +1 for newer. */
    private void recallHistory(int delta) {
        List<String> history = myStateService.getCommandHistory();
        if (history.isEmpty()) {
            return;
        }
        if (myHistoryIndex == -1) {
            // Entering history browsing — remember the draft so the bottom of the list restores it.
            myDraft = myEditor.getText();
            myHistoryIndex = history.size();
        }
        myHistoryIndex = Math.clamp(myHistoryIndex + delta, 0, history.size());
        setFieldText(myHistoryIndex == history.size() ? myDraft : history.get(myHistoryIndex));
    }

    /** Replaces the field text without resetting the history-browsing position. */
    private void setFieldText(String text) {
        mySuppressHistoryReset = true;
        myEditor.setText(text);
        mySuppressHistoryReset = false;
        myEditor.setCaretPosition(myEditor.getText().length());
    }

    private void runCommand(String command, Path workingDir) {
        UiTask.background(() -> {
            try {
                Process process = new ProcessBuilder(commandLine(command))
                    .directory(workingDir.toFile())
                    .start();
                // A long-running command (a shell, an editor) shouldn't hold the refresh hostage —
                // refresh once it passes the threshold, then keep waiting for the real exit.
                if (!process.waitFor(LONG_RUN_REFRESH_MS, TimeUnit.MILLISECONDS)) {
                    refreshPanels();
                    process.waitFor();
                }
                reportFailure(command, process);
            }
            catch (Exception e) {
                notifyErrorLater("Failed to run \"" + command + "\": " + e.getMessage());
            }
            refreshPanels();
        });
    }

    private static List<String> commandLine(String command) {
        if (!SystemInfo.isWindows) {
            return List.of("/bin/sh", "-c", command);
        }
        // A bare shell needs its own console window, which only `start` gives it.
        if (BARE_SHELLS.contains(StringUtil.toLowerCase(command))) {
            return List.of("cmd.exe", "/c", "start", "", command);
        }
        return List.of("cmd.exe", "/c", command);
    }

    private void reportFailure(String command, Process process) throws Exception {
        int exitCode = process.exitValue();
        if (exitCode == 0) {
            return;
        }
        String stderr = readStream(process.getErrorStream());
        String detail = StringUtil.first(
            (stderr.isBlank() ? readStream(process.getInputStream()) : stderr).trim(),
            MAX_ERROR_DETAIL, true);

        String message = "Command exited with code " + exitCode + ": " + command;
        notifyErrorLater(detail.isEmpty() ? message : message + "\n" + detail);
    }

    private static String readStream(java.io.InputStream stream) throws Exception {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void refreshPanels() {
        UiTask.ui(() -> {
            myLeftPanel.refreshActiveTab(false);
            myRightPanel.refreshActiveTab(false);
        });
    }

    private void notifyErrorLater(String content) {
        UiTask.ui(() -> TurtleCommanderNotifications.error(myProject, content));
    }
}
