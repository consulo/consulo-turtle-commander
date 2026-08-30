package consulo.turtlecommander.service;

import java.nio.file.Path;

/** Reports a per-entry failure without aborting the surrounding operation. */
@FunctionalInterface
public interface ErrorListener {
    void onError(Path path, Exception error);
}
