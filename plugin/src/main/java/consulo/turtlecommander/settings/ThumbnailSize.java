package consulo.turtlecommander.settings;

/**
 * Preset sizes for the Thumbnail view's cells.
 *
 * <p>{@code cacheSize} is ~3x {@code displaySize} so HiDPI displays still get a sharp render
 * after the bicubic paint-time downscale. {@link #SMALL} matches the original hardcoded layout.
 */
public enum ThumbnailSize {
    SMALL(64, 192, 120, 90, "Small (64 px)"),
    MEDIUM(96, 256, 152, 122, "Medium (96 px)"),
    LARGE(128, 320, 184, 154, "Large (128 px)"),
    EXTRA_LARGE(160, 384, 216, 186, "Extra Large (160 px)");

    private final int myDisplaySize;
    private final int myCacheSize;
    private final int myCellWidth;
    private final int myCellHeight;
    private final String myLabel;

    ThumbnailSize(int displaySize, int cacheSize, int cellWidth, int cellHeight, String label) {
        myDisplaySize = displaySize;
        myCacheSize = cacheSize;
        myCellWidth = cellWidth;
        myCellHeight = cellHeight;
        myLabel = label;
    }

    public int getDisplaySize() {
        return myDisplaySize;
    }

    public int getCacheSize() {
        return myCacheSize;
    }

    public int getCellWidth() {
        return myCellWidth;
    }

    public int getCellHeight() {
        return myCellHeight;
    }

    public String getLabel() {
        return myLabel;
    }

    public static ThumbnailSize fromName(String name) {
        for (ThumbnailSize size : values()) {
            if (size.name().equals(name)) {
                return size;
            }
        }
        return SMALL;
    }
}
