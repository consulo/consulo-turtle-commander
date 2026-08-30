package consulo.turtlecommander.service;

import java.nio.file.Path;

/**
 * Asks the user what to do about an existing target. Implementations block the calling worker
 * thread while a modal dialog is shown, so this must never be invoked on the UI thread.
 */
@FunctionalInterface
public interface OverwriteConfirmation {
    OverwriteResponse confirm(Path target);
}
