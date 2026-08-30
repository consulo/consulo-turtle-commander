package consulo.turtlecommander.service;

/** Reports how many entries have been processed and which one is current. */
@FunctionalInterface
public interface ProgressListener {
    void onProgress(int processedCount, String currentName);
}
