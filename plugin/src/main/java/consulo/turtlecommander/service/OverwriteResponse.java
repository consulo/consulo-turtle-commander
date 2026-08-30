package consulo.turtlecommander.service;

/**
 * One-shot decision from the per-file overwrite prompt. The values cover the practical
 * Total-Commander-style button set: act on this one, act on all, the mtime-aware "if older"
 * variants, and a global cancel.
 */
public enum OverwriteResponse {
    OVERWRITE,
    SKIP,
    OVERWRITE_ALL,
    SKIP_ALL,
    /** Overwrite this single file only if the source mtime is strictly newer than the target. */
    OVERWRITE_IF_OLDER,
    /** Apply the if-older rule to this file and every remaining file in the run. */
    OVERWRITE_ALL_IF_OLDER,
    /** Abort the whole copy/move/extract operation immediately. */
    CANCEL
}
