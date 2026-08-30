package consulo.turtlecommander.service;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.container.boot.ContainerPathManager;
import consulo.disposer.Disposable;
import consulo.logging.Logger;
import consulo.turtlecommander.settings.ThumbnailSize;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.vfs.OpenVfsRegistry;
import consulo.util.io.FileUtil;
import consulo.ui.ex.awt.ImageUtil;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class ThumbnailCache implements Disposable {
    private static final Logger LOG = Logger.getInstance(ThumbnailCache.class);

    private static final String CACHE_DIR_NAME = "turtle-commander-thumbnails";
    private static final int MAX_CONCURRENT_LOADS = 4;

    private static final Set<String> IMAGE_EXTENSIONS =
        Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp", "ico", "tif", "tiff");

    /**
     * Bounded pool giving the same "at most N concurrent loads" guarantee the coroutine version got
     * from a limited-parallelism dispatcher. Daemon threads so a stuck decode can't hold shutdown.
     */
    private final ExecutorService myExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_LOADS,
        new ThreadFactory() {
            private final AtomicInteger myCounter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "TurtleCommander Thumbnails " + myCounter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });

    /** Absolute file path to loaded thumbnail icon. */
    private final Map<Path, Icon> myMemoryCache = new ConcurrentHashMap<>();

    /** Files currently being loaded, so duplicate requests collapse. */
    private final Set<Path> myLoading = ConcurrentHashMap.newKeySet();

    private volatile @Nullable Path myCacheDir;

    public static ThumbnailCache getInstance() {
        return Application.get().getInstance(ThumbnailCache.class);
    }

    public static boolean isImageFile(String name) {
        return IMAGE_EXTENSIONS.contains(FileUtil.getExtension(name).toLowerCase(Locale.ROOT));
    }

    public @Nullable Icon getCachedThumbnail(Path path) {
        return myMemoryCache.get(path);
    }

    /**
     * Drops the in-memory icons without touching the on-disk cache. Called when the user changes
     * the thumbnail size preset, so visible cells re-render at the new logical size.
     */
    public void clearMemoryCache() {
        myMemoryCache.clear();
    }

    /**
     * Fire-and-forget load. {@code onReady} runs on the UI thread once the icon is cached, so
     * callers don't need their own invokeLater. Cancellation is cooperative: {@code isStillVisible}
     * is re-checked before each expensive step.
     */
    public void requestThumbnail(Path path, @Nullable FileTime lastModified, BooleanSupplier isStillVisible,
                                 Runnable onReady) {
        if (myMemoryCache.containsKey(path) || !myLoading.add(path)) {
            return;
        }

        myExecutor.execute(() -> {
            try {
                if (myMemoryCache.containsKey(path) || !isStillVisible.getAsBoolean()) {
                    return;
                }
                Icon icon = loadOrCreateThumbnail(path, lastModified);
                if (icon == null || !isStillVisible.getAsBoolean()) {
                    return;
                }
                myMemoryCache.putIfAbsent(path, icon);
                Application.get().invokeLater(onReady);
            }
            catch (Exception e) {
                LOG.debug("Failed to create thumbnail for " + path, e);
            }
            finally {
                myLoading.remove(path);
            }
        });
    }

    public void evictDirectory(Path directory) {
        List<Path> evicted = new ArrayList<>();
        myMemoryCache.keySet().removeIf(key -> {
            if (key.startsWith(directory)) {
                evicted.add(key);
                return true;
            }
            return false;
        });

        if (evicted.isEmpty()) {
            return;
        }

        myExecutor.execute(() -> {
            for (Path sourcePath : evicted) {
                try {
                    Path cachePath = getCachePath(sourcePath);
                    if (cachePath != null) {
                        Files.deleteIfExists(cachePath);
                    }
                }
                catch (Exception e) {
                    // A cache file we can't remove is replaced on the next generation anyway.
                }
            }
        });
    }

    public long getCacheSize() {
        try {
            Path dir = cacheDir();
            if (!Files.isDirectory(dir)) {
                return 0L;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                return stream.mapToLong(path -> {
                    try {
                        return Files.size(path);
                    }
                    catch (IOException e) {
                        return 0L;
                    }
                }).sum();
            }
        }
        catch (Exception e) {
            return 0L;
        }
    }

    public void clearCache() {
        myMemoryCache.clear();
        try {
            Path dir = cacheDir();
            if (!Files.isDirectory(dir)) {
                return;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                stream.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    }
                    catch (IOException ignored) {
                        // Best effort.
                    }
                });
            }
        }
        catch (Exception e) {
            LOG.debug("Failed to clear thumbnail cache", e);
        }
    }

    @Override
    public void dispose() {
        myExecutor.shutdownNow();
    }

    /**
     * Default-size cache path, used by {@link #evictDirectory} which doesn't know what size an entry
     * was generated at. Evicting the in-memory entry forces regeneration; orphaned disk entries are
     * replaced or wiped on the next manual clear.
     */
    @Nullable
    Path getCachePath(Path sourcePath) {
        return getCachePath(sourcePath, currentSize().getCacheSize());
    }

    @Nullable
    Path getCachePath(Path sourcePath, int cacheSize) {
        try {
            // A hash of the absolute path avoids filesystem limits on long paths. The cache size is
            // part of the name so different presets coexist, and entries from the old hardcoded
            // 64px layout (which had no size segment) are naturally bypassed.
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(sourcePath.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hash = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                hash.append(String.format("%02x", digest[i]));
            }

            Path fileName = sourcePath.getFileName();
            if (fileName == null) {
                return null;
            }
            return cacheDir().resolve(hash + "-" + cacheSize + "-" + fileName + ".png");
        }
        catch (Exception e) {
            return null;
        }
    }

    private Path cacheDir() throws IOException {
        Path dir = myCacheDir;
        if (dir == null) {
            dir = Path.of(ContainerPathManager.get().getSystemPath(), CACHE_DIR_NAME);
            Files.createDirectories(dir);
            myCacheDir = dir;
        }
        return dir;
    }

    private static ThumbnailSize currentSize() {
        return ThumbnailSize.fromName(TurtleCommanderSettings.getInstance().getState().thumbnailSize);
    }

    private @Nullable Icon loadOrCreateThumbnail(Path path, @Nullable FileTime lastModified) throws IOException {
        ThumbnailSize size = currentSize();
        Path cachePath = getCachePath(path, size.getCacheSize());
        long modMillis = lastModified != null ? lastModified.toMillis() : 0L;

        if (cachePath != null && Files.exists(cachePath)
            && Files.getLastModifiedTime(cachePath).toMillis() >= modMillis) {
            BufferedImage cached = ImageIO.read(cachePath.toFile());
            if (cached != null) {
                return new HighQualityImageIcon(cached, size.getDisplaySize());
            }
        }

        BufferedImage thumb = readSubsampledThumbnail(path, size.getCacheSize());
        if (thumb == null) {
            return null;
        }

        if (cachePath != null) {
            try {
                Path parent = cachePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                ImageIO.write(thumb, "png", cachePath.toFile());
                if (modMillis > 0) {
                    Files.setLastModifiedTime(cachePath, FileTime.fromMillis(modMillis));
                }
            }
            catch (Exception e) {
                LOG.debug("Failed to write thumbnail cache for " + path, e);
            }
        }

        return new HighQualityImageIcon(thumb, size.getDisplaySize());
    }

    private static @Nullable BufferedImage readSubsampledThumbnail(Path path, int targetSize) throws IOException {
        // ImageIO.createImageInputStream goes through a FileInputStream — a sparse stub in a lazy
        // filesystem would yield zero bytes and no readable thumbnail. Materialise first; a no-op
        // for files outside any archive or in fully-extracted ones.
        OpenVfsRegistry.materializeIfNeeded(path);

        ImageInputStream imageStream;
        try {
            imageStream = ImageIO.createImageInputStream(path.toFile());
        }
        catch (Exception e) {
            return null;
        }
        if (imageStream == null) {
            return null;
        }

        try (ImageInputStream stream = imageStream) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int maxDim = Math.max(width, height);

                ImageReadParam param = reader.getDefaultReadParam();
                // Two-step downscale: subsample to ~2x the target so the bicubic pass below has
                // enough source data to stay sharp. Subsampling alone is a nearest-neighbour pick
                // and aliases visibly on photographic content.
                int subsampleTarget = targetSize * 2;
                if (maxDim > subsampleTarget) {
                    int subsample = Math.max(1, maxDim / subsampleTarget);
                    param.setSourceSubsampling(subsample, subsample, 0, 0);
                }

                BufferedImage subsampled = reader.read(0, param);
                if (subsampled.getWidth() <= targetSize && subsampled.getHeight() <= targetSize) {
                    return subsampled;
                }
                return createScaledThumbnail(subsampled, targetSize);
            }
            finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage createScaledThumbnail(BufferedImage source, int targetSize) {
        double scale = (double)targetSize / Math.max(source.getWidth(), source.getHeight());
        // The platform's multi-step scaler beats a single hand-rolled bicubic pass;
        // toBufferedImage gives back a writable raster for the PNG disk cache.
        return ImageUtil.toBufferedImage(ImageUtil.scaleImage(source, scale));
    }

    /**
     * Icon backed by a high-resolution bitmap. Painting scales it to a fixed logical size with
     * bicubic interpolation, so on HiDPI the Graphics2D device-scale transform turns the larger
     * source bitmap into a sharp render at physical pixel resolution.
     */
    private static final class HighQualityImageIcon implements Icon {
        private final BufferedImage myImage;
        private final int myDisplayWidth;
        private final int myDisplayHeight;

        HighQualityImageIcon(BufferedImage image, int maxLogicalSize) {
            myImage = image;
            int maxDim = Math.max(image.getWidth(), image.getHeight());
            if (maxDim <= maxLogicalSize) {
                myDisplayWidth = image.getWidth();
                myDisplayHeight = image.getHeight();
            }
            else {
                double scale = (double)maxLogicalSize / maxDim;
                myDisplayWidth = Math.max(1, (int)(image.getWidth() * scale));
                myDisplayHeight = Math.max(1, (int)(image.getHeight() * scale));
            }
        }

        @Override
        public int getIconWidth() {
            return myDisplayWidth;
        }

        @Override
        public int getIconHeight() {
            return myDisplayHeight;
        }

        @Override
        public void paintIcon(@Nullable Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D)g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.drawImage(myImage, x, y, myDisplayWidth, myDisplayHeight, null);
            }
            finally {
                g2.dispose();
            }
        }
    }
}
