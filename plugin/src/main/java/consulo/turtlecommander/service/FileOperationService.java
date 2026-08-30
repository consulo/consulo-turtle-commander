package consulo.turtlecommander.service;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.util.SystemInfo;
import consulo.disposer.Disposable;
import consulo.logging.Logger;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.settings.ColorRule;
import consulo.turtlecommander.settings.ColorRuleManager;
import consulo.turtlecommander.settings.ColumnConfig;
import consulo.turtlecommander.settings.RuleMatcher;
import consulo.turtlecommander.settings.TextProperty;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.util.DriveLabels;
import consulo.turtlecommander.util.FileAttributes;
import consulo.turtlecommander.vfs.OpenVfsRegistry;
import consulo.turtlecommander.vfs.VfsPaths;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Directory listing (with cache) and the copy / move / delete / link operations.
 *
 * <p>Every operation blocks: callers run them off the UI thread and drive cancellation through the
 * supplied predicate. The one exception is the shared drive-roots poller, which owns a scheduled
 * thread and notifies listeners on the UI thread.
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class FileOperationService implements Disposable {
    private static final Logger LOG = Logger.getInstance(FileOperationService.class);

    static final int CACHE_MAX_SIZE = 20;
    /** 2 minutes; mtime invalidation handles most staleness. */
    static final long CACHE_TTL_MS = 2L * 60 * 1000;
    /** Matches the historical per-tab Swing timer. */
    static final long ROOTS_POLL_INTERVAL_MS = 5_000L;

    /**
     * What {@link #resolveOverwriteAction} tells the caller to do for a single existing target.
     * CANCEL aborts the whole loop, SKIP continues to the next source, OVERWRITE means replace now.
     */
    public enum TargetAction {
        OVERWRITE, SKIP, CANCEL
    }

    /** Mutable holder so one policy can be threaded through the nested helpers. */
    public static final class PolicyHolder {
        private OverwritePolicy myPolicy;

        public PolicyHolder(OverwritePolicy policy) {
            myPolicy = policy;
        }

        public OverwritePolicy getPolicy() {
            return myPolicy;
        }

        public void setPolicy(OverwritePolicy policy) {
            myPolicy = policy;
        }
    }

    private record CachedListing(List<FileEntry> dirs, List<FileEntry> files, long timestamp,
                                 @Nullable FileTime dirMtime) {
    }

    private record EntryReadNeeds(boolean owner, boolean group, boolean permissions, boolean inode, boolean links) {
    }

    private record MoveDirResult(int movedCount, boolean cancelled, boolean leftBehind) {
    }

    private record CountAndCancelled(int count, boolean cancelled) {
    }

    private final boolean myWindows = SystemInfo.isWindows;
    private final boolean myMac = SystemInfo.isMac;

    private final Map<Path, CachedListing> myListingCache =
        new LinkedHashMap<>(CACHE_MAX_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Path, CachedListing> eldest) {
                return size() > CACHE_MAX_SIZE;
            }
        };

    /** Test-only clock override. */
    private LongSupplier myClock = System::currentTimeMillis;

    /**
     * Test-only hook for the "Delete to Recycle Bin" flow. Tests replace it with an in-memory fake
     * so they neither depend on a real trash on CI nor litter the developer's own.
     */
    private Predicate<Path> myMoveToTrash = path -> {
        try {
            return Desktop.getDesktop().moveToTrash(path.toFile());
        }
        catch (UnsupportedOperationException e) {
            return false;
        }
    };

    /** Mirrors {@link #myMoveToTrash} so tests can exercise the path without a graphical desktop. */
    private BooleanSupplier myMoveToTrashSupported = () -> {
        try {
            return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
        }
        catch (Throwable e) {
            return false;
        }
    };

    // --- Shared drive-roots polling -------------------------------------------------------------
    // Each tab used to run its own 5-second timer around getRoots(), and also called it
    // synchronously on the UI thread at construction — File.listRoots() can block for seconds on
    // Windows (disconnected network drives, empty card readers). One shared poller serves every
    // subscribed tab: it scans on a background thread, warms the drive-label cache there too, and
    // notifies listeners on the UI thread only when the list actually changes.

    private final List<Consumer<List<String>>> myRootsListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService myRootsScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "TurtleCommander Drive Roots");
        thread.setDaemon(true);
        return thread;
    });

    private volatile @Nullable List<String> myCachedRoots;
    private @Nullable ScheduledFuture<?> myRootsPollHandle;

    /**
     * Whether this platform can move files to the OS trash. The delete UI checks this before
     * offering the recycle-bin dialog, so the user is never shown a reversible operation that would
     * actually run as a permanent delete.
     */
    public boolean isRecycleBinAvailable() {
        return myMoveToTrashSupported.getAsBoolean();
    }

    // --- Listing --------------------------------------------------------------------------------

    public List<FileEntry> listFiles(Path directory) throws IOException {
        return listFiles(directory, false);
    }

    public List<FileEntry> listFiles(Path directory, boolean forceRefresh) throws IOException {
        List<FileEntry> result = new ArrayList<>();

        Path parent = directory.getParent();
        if (parent != null) {
            result.add(VfsPaths.parentEntry(parent));
        }

        CachedListing cached = forceRefresh ? null : getCachedListing(directory);
        if (cached != null) {
            appendSortedEntries(result, cached.dirs(), cached.files());
            return result;
        }

        Path effectiveDirectory;
        try {
            effectiveDirectory = resolveReparsePoint(directory);
        }
        catch (Exception e) {
            effectiveDirectory = directory;
        }

        List<FileEntry> dirs = new ArrayList<>();
        List<FileEntry> files = new ArrayList<>();
        EntryReadNeeds needs = entryReadNeeds();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(effectiveDirectory)) {
            for (Path entry : stream) {
                FileEntry fileEntry = buildFileEntry(entry, needs);
                if (fileEntry == null) {
                    continue;
                }
                if (fileEntry.directory()) {
                    dirs.add(fileEntry);
                }
                else {
                    files.add(fileEntry);
                }
            }
        }

        FileTime mtime;
        try {
            mtime = Files.getLastModifiedTime(effectiveDirectory);
        }
        catch (Exception e) {
            mtime = null;
        }

        putCachedListing(directory, List.copyOf(dirs), List.copyOf(files), mtime);
        appendSortedEntries(result, dirs, files);
        return result;
    }

    public List<FileEntry> listAllNestedFiles(Path directory) {
        List<FileEntry> result = new ArrayList<>();
        Path parent = directory.getParent();
        if (parent != null) {
            result.add(VfsPaths.parentEntry(parent));
        }

        EntryReadNeeds needs = entryReadNeeds();
        List<FileEntry> files = new ArrayList<>();

        Path start;
        try {
            start = resolveReparsePoint(directory);
        }
        catch (Exception e) {
            start = directory;
        }

        try {
            Files.walkFileTree(start, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // buildFileEntry follows symlinks to classify them, so a link to a directory
                    // resolves as a directory and is excluded — only true files are kept.
                    FileEntry entry = buildFileEntry(file, needs);
                    if (entry != null && !entry.directory()) {
                        files.add(entry);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            LOG.debug("Cannot walk directory " + directory, e);
        }

        files.sort(Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
        result.addAll(files);
        return result;
    }

    /**
     * Which expensive per-entry lookups are actually displayed. On Windows the User column is hidden
     * by default, so {@code Files.getOwner} (a slow Win32 security-descriptor + SID-to-name lookup)
     * shouldn't run for every file in a large directory. Owner / group / permissions are also read
     * when an active colour rule matches on them, otherwise such a rule could never fire while its
     * column is hidden. Created/modified timestamps come from the same attribute read as size, so
     * they need no opt-in.
     */
    private EntryReadNeeds entryReadNeeds() {
        TurtleCommanderSettings settings = TurtleCommanderSettings.getInstance();

        Set<String> visibleColumnIds = new HashSet<>();
        for (ColumnConfig column : settings.getEffectiveColumns()) {
            if (column.visible) {
                visibleColumnIds.add(column.id);
            }
        }

        TurtleCommanderSettings.State state = settings.getState();
        List<ColorRule> activeRules = state.enableFileNameHighlighting
            ? ColorRuleManager.getActiveRulesForEvaluation(state)
            : List.of();

        return new EntryReadNeeds(
            visibleColumnIds.contains("User") || rulesMatchOn(activeRules, TextProperty.OWNER),
            !myWindows && (visibleColumnIds.contains("Group") || rulesMatchOn(activeRules, TextProperty.GROUP)),
            visibleColumnIds.contains("Permissions") || rulesMatchOn(activeRules, TextProperty.PERMISSIONS),
            // Inode and Links are Unix-only and hidden by default; read them only when shown.
            visibleColumnIds.contains("Inode"),
            visibleColumnIds.contains("Links")
        );
    }

    private static boolean rulesMatchOn(List<ColorRule> rules, TextProperty property) {
        for (ColorRule rule : rules) {
            for (RuleMatcher matcher : rule.matchers()) {
                if (matcher instanceof RuleMatcher.Text text && text.field() == property) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Reads attributes for one directory entry, or returns null (with a debug log) when the read
     * fails — usually a file disappearing mid-iteration, which shouldn't abort the whole listing.
     */
    private @Nullable FileEntry buildFileEntry(Path entry, EntryReadNeeds needs) {
        try {
            // Read with NOFOLLOW first: a single stat that tells us whether the entry is a symlink,
            // and for the common non-link case it already is the real attributes (no second
            // syscall). On Windows read DosFileAttributes directly so permission flags come from
            // the same syscall as size/time.
            boolean dosAttrs = myWindows && needs.permissions();
            BasicFileAttributes linkAttrs = dosAttrs
                ? Files.readAttributes(entry, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                : Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

            boolean isLink = linkAttrs.isSymbolicLink();
            BasicFileAttributes attrs = linkAttrs;
            boolean broken = false;
            String linkTarget = null;

            if (isLink) {
                try {
                    linkTarget = Files.readSymbolicLink(entry).toString();
                }
                catch (Exception e) {
                    linkTarget = null;
                }
                // Follow the link to describe its target. A failure means a dangling link — keep
                // the entry using the link's own attrs and flag it broken rather than dropping it.
                try {
                    attrs = dosAttrs
                        ? Files.readAttributes(entry, DosFileAttributes.class)
                        : Files.readAttributes(entry, BasicFileAttributes.class);
                }
                catch (IOException e) {
                    broken = true;
                    attrs = linkAttrs;
                }
            }

            String permissions;
            if (!needs.permissions()) {
                permissions = "";
            }
            else if (attrs instanceof DosFileAttributes dos) {
                permissions = dosAttrsToString(dos);
            }
            else {
                permissions = FileAttributes.readPermissions(entry, myWindows);
            }

            return FileEntry.builder(entry.getFileName().toString(), entry)
                .directory(attrs.isDirectory())
                .size(attrs.size())
                .creationTime(attrs.creationTime())
                .lastModified(attrs.lastModifiedTime())
                .owner(needs.owner() ? FileAttributes.readOwner(entry) : "")
                .group(needs.group() ? FileAttributes.readGroup(entry) : "")
                .permissions(permissions)
                .inode(needs.inode() ? FileAttributes.readInode(entry) : null)
                .nlink(needs.links() ? FileAttributes.readNlink(entry) : null)
                .symbolicLink(isLink)
                .brokenSymlink(broken)
                .linkTarget(linkTarget)
                .namedPipe(!myWindows && !isLink && attrs.isOther() && FileAttributes.isNamedPipe(entry))
                .build();
        }
        catch (IOException e) {
            LOG.debug("Cannot read attributes for " + entry, e);
            return null;
        }
    }

    private static void appendSortedEntries(List<FileEntry> result, List<FileEntry> dirs, List<FileEntry> files) {
        Comparator<FileEntry> byName = Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT));

        if (TurtleCommanderSettings.getInstance().getState().sortWithDirectories) {
            List<FileEntry> all = new ArrayList<>(dirs.size() + files.size());
            all.addAll(dirs);
            all.addAll(files);
            all.sort(byName);
            result.addAll(all);
            return;
        }

        List<FileEntry> sortedDirs = new ArrayList<>(dirs);
        List<FileEntry> sortedFiles = new ArrayList<>(files);
        sortedDirs.sort(byName);
        sortedFiles.sort(byName);
        result.addAll(sortedDirs);
        result.addAll(sortedFiles);
    }

    // --- Listing cache --------------------------------------------------------------------------

    private @Nullable CachedListing getCachedListing(Path directory) {
        CachedListing entry;
        synchronized (myListingCache) {
            entry = myListingCache.get(directory);
            if (entry == null) {
                return null;
            }
            if (myClock.getAsLong() - entry.timestamp() > CACHE_TTL_MS) {
                myListingCache.remove(directory);
                return null;
            }
        }

        // The mtime check runs outside the lock so a filesystem stat can't block other cache users.
        // A racing put can't corrupt the result — worst case a freshly-repopulated entry is evicted,
        // which is a self-healing no-op on the next listFiles.
        if (entry.dirMtime() != null) {
            FileTime current;
            try {
                current = Files.getLastModifiedTime(directory);
            }
            catch (Exception e) {
                current = null;
            }
            if (current == null || !current.equals(entry.dirMtime())) {
                synchronized (myListingCache) {
                    myListingCache.remove(directory);
                }
                return null;
            }
        }
        return entry;
    }

    private void putCachedListing(Path directory, List<FileEntry> dirs, List<FileEntry> files,
                                  @Nullable FileTime dirMtime) {
        synchronized (myListingCache) {
            myListingCache.put(directory, new CachedListing(dirs, files, myClock.getAsLong(), dirMtime));
        }
    }

    public void invalidateListingCache(Path directory) {
        synchronized (myListingCache) {
            myListingCache.remove(directory);
        }
    }

    public void clearListingCache() {
        synchronized (myListingCache) {
            myListingCache.clear();
        }
    }

    int listingCacheSize() {
        synchronized (myListingCache) {
            return myListingCache.size();
        }
    }

    boolean isListingCached(Path directory) {
        synchronized (myListingCache) {
            CachedListing entry = myListingCache.get(directory);
            return entry != null && myClock.getAsLong() - entry.timestamp() <= CACHE_TTL_MS;
        }
    }

    /**
     * Like {@link #isListingCached} but also verifies the directory's mtime still matches. False
     * when the listing would be re-read on the next call — the UI uses this to skip a costly
     * re-render on tab activation when nothing changed.
     */
    public boolean isListingCacheFresh(Path directory) {
        CachedListing entry;
        synchronized (myListingCache) {
            entry = myListingCache.get(directory);
            if (entry == null || myClock.getAsLong() - entry.timestamp() > CACHE_TTL_MS) {
                return false;
            }
        }
        if (entry.dirMtime() == null) {
            return true;
        }
        try {
            return Files.getLastModifiedTime(directory).equals(entry.dirMtime());
        }
        catch (Exception e) {
            return false;
        }
    }

    // --- Overwrite resolution -------------------------------------------------------------------

    /**
     * What to do when {@code target} already exists, given the current policy. For
     * {@link OverwritePolicy#ASK} this delegates to the confirmation and translates the seven-button
     * response into a {@link TargetAction}, possibly upgrading the policy so later files in the run
     * answer without prompting.
     */
    TargetAction resolveOverwriteAction(Path source, Path target, PolicyHolder holder,
                                        OverwriteConfirmation onOverwriteConfirm) {
        switch (holder.getPolicy()) {
            case OVERWRITE_ALL:
                return TargetAction.OVERWRITE;
            case SKIP_ALL:
                return TargetAction.SKIP;
            case IF_OLDER:
                return ifOlderAction(source, target);
            case ASK:
            default:
                break;
        }

        return switch (onOverwriteConfirm.confirm(target)) {
            case OVERWRITE -> TargetAction.OVERWRITE;
            case SKIP -> TargetAction.SKIP;
            case OVERWRITE_ALL -> {
                holder.setPolicy(OverwritePolicy.OVERWRITE_ALL);
                yield TargetAction.OVERWRITE;
            }
            case SKIP_ALL -> {
                holder.setPolicy(OverwritePolicy.SKIP_ALL);
                yield TargetAction.SKIP;
            }
            case OVERWRITE_IF_OLDER -> ifOlderAction(source, target);
            case OVERWRITE_ALL_IF_OLDER -> {
                holder.setPolicy(OverwritePolicy.IF_OLDER);
                yield ifOlderAction(source, target);
            }
            case CANCEL -> TargetAction.CANCEL;
        };
    }

    private static TargetAction ifOlderAction(Path source, Path target) {
        boolean sourceNewer;
        try {
            sourceNewer = Files.getLastModifiedTime(source).compareTo(Files.getLastModifiedTime(target)) > 0;
        }
        catch (Exception e) {
            // mtime unreadable on either side — overwrite, so the user's intent ("replace stale
            // targets") wins over a transient stat failure.
            sourceNewer = true;
        }
        return sourceNewer ? TargetAction.OVERWRITE : TargetAction.SKIP;
    }

    private static boolean targetIsInsideSource(Path source, Path target) {
        try {
            return target.toAbsolutePath().normalize().startsWith(source.toAbsolutePath().normalize());
        }
        catch (Exception e) {
            return false;
        }
    }

    // --- Copy -----------------------------------------------------------------------------------

    public void copyFilesWithProgress(List<Path> sources, Path destination, OverwritePolicy initialPolicy,
                                      ProgressListener onProgress, OverwriteConfirmation onOverwriteConfirm,
                                      ErrorListener onError, BooleanSupplier isCancelled) {
        int copiedCount = 0;
        PolicyHolder holder = new PolicyHolder(initialPolicy);

        for (Path source : sources) {
            if (isCancelled.getAsBoolean()) {
                break;
            }

            String sourceName = source.getFileName().toString();
            Path target = destination.resolve(sourceName);
            if (targetIsInsideSource(source, target)) {
                onError.onError(source, new IOException("Cannot copy \"" + sourceName + "\" into itself"));
                continue;
            }

            try {
                boolean sourceIsLink = Files.isSymbolicLink(source);
                if (Files.isDirectory(source) && !sourceIsLink) {
                    CountAndCancelled result = copyDirectoryWithProgress(source, target, copiedCount, holder,
                        onProgress, onOverwriteConfirm, onError, isCancelled);
                    copiedCount = result.count();
                    if (result.cancelled()) {
                        break;
                    }
                }
                else {
                    TargetAction action = copyFileWithOverwrite(source, target, holder, onOverwriteConfirm,
                        sourceIsLink);
                    if (action == TargetAction.CANCEL) {
                        break;
                    }
                    if (action == TargetAction.OVERWRITE) {
                        copiedCount++;
                        onProgress.onProgress(copiedCount, sourceName);
                    }
                }
            }
            catch (Exception e) {
                LOG.warn("Failed to copy " + source + " to " + destination, e);
                onError.onError(source, e);
            }
        }

        invalidateDestinationTree(destination, sources);
    }

    /**
     * Invalidates the listing cache for {@code destination} and every nested directory the copy
     * actually wrote into. Without this, a tab viewing a sub-directory of the destination keeps
     * serving the pre-copy listing.
     */
    private void invalidateDestinationTree(Path destination, List<Path> sources) {
        invalidateListingCache(destination);

        for (Path source : sources) {
            if (!Files.isDirectory(source)) {
                continue;
            }
            Path target = destination.resolve(source.getFileName().toString());
            if (!Files.isDirectory(target)) {
                continue;
            }
            try {
                Files.walkFileTree(target, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        invalidateListingCache(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            catch (Exception e) {
                LOG.debug("Failed to walk " + target + " for cache invalidation", e);
            }
        }
    }

    /**
     * Copies one file honouring the policy. Returns OVERWRITE when the file was actually written
     * (so the caller can tick progress), SKIP when the policy chose to skip it, or CANCEL when the
     * user aborted.
     */
    private TargetAction copyFileWithOverwrite(Path source, Path target, PolicyHolder holder,
                                               OverwriteConfirmation onOverwriteConfirm, boolean nofollow)
        throws IOException {

        // Stream bytes onto the stub if the source lives in a lazy filesystem. A no-op for ordinary
        // files and for filesystems that extract eagerly.
        OpenVfsRegistry.materializeIfNeeded(source);

        // For a symlink, copy the link node itself rather than following it to its target — that
        // avoids duplicating the target's content and cyclic-link loops.
        CopyOption[] linkOpts = nofollow
            ? new CopyOption[]{LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES}
            : new CopyOption[0];

        if (!Files.exists(target)) {
            Files.copy(source, target, linkOpts);
            return TargetAction.OVERWRITE;
        }

        TargetAction action = resolveOverwriteAction(source, target, holder, onOverwriteConfirm);
        if (action == TargetAction.OVERWRITE) {
            Files.copy(source, target, withReplaceExisting(linkOpts));
        }
        return action;
    }

    private CountAndCancelled copyDirectoryWithProgress(Path source, Path target, int startCount,
                                                        PolicyHolder holder, ProgressListener onProgress,
                                                        OverwriteConfirmation onOverwriteConfirm,
                                                        ErrorListener onError, BooleanSupplier isCancelled) {
        int copiedCount = startCount;

        try {
            // Tick progress only when the directory is actually created — when merging into an
            // existing tree the pre-existing directory isn't "copied" work, same as a skipped file.
            // A target that exists as a regular file makes createDirectories throw, landing below.
            boolean alreadyExists = Files.isDirectory(target);
            Files.createDirectories(target);
            if (!alreadyExists) {
                copiedCount++;
                onProgress.onProgress(copiedCount, source.getFileName().toString());
            }
        }
        catch (Exception e) {
            LOG.warn("Failed to create directory " + target, e);
            onError.onError(source, e);
            return new CountAndCancelled(copiedCount, false);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (Path entry : stream) {
                if (isCancelled.getAsBoolean()) {
                    break;
                }

                String entryName = entry.getFileName().toString();
                Path entryTarget = target.resolve(entryName);
                boolean entryIsLink = Files.isSymbolicLink(entry);

                if (Files.isDirectory(entry) && !entryIsLink) {
                    CountAndCancelled sub = copyDirectoryWithProgress(entry, entryTarget, copiedCount, holder,
                        onProgress, onOverwriteConfirm, onError, isCancelled);
                    copiedCount = sub.count();
                    if (sub.cancelled()) {
                        return new CountAndCancelled(copiedCount, true);
                    }
                }
                else {
                    try {
                        TargetAction action = copyFileWithOverwrite(entry, entryTarget, holder,
                            onOverwriteConfirm, entryIsLink);
                        if (action == TargetAction.CANCEL) {
                            return new CountAndCancelled(copiedCount, true);
                        }
                        if (action == TargetAction.OVERWRITE) {
                            copiedCount++;
                            onProgress.onProgress(copiedCount, entryName);
                        }
                    }
                    catch (Exception e) {
                        LOG.warn("Failed to copy " + entry, e);
                        onError.onError(entry, e);
                    }
                }
            }
        }
        catch (Exception e) {
            LOG.warn("Failed to list directory " + source, e);
            onError.onError(source, e);
        }

        return new CountAndCancelled(copiedCount, false);
    }

    // --- Move -----------------------------------------------------------------------------------

    public void moveFilesWithProgress(List<Path> sources, Path destination, OverwritePolicy initialPolicy,
                                      ProgressListener onProgress, OverwriteConfirmation onOverwriteConfirm,
                                      ErrorListener onError, BooleanSupplier isCancelled) {
        int movedCount = 0;
        PolicyHolder holder = new PolicyHolder(initialPolicy);

        for (Path source : sources) {
            if (isCancelled.getAsBoolean()) {
                break;
            }

            String sourceName = source.getFileName().toString();
            Path target = destination.resolve(sourceName);
            if (targetIsInsideSource(source, target)) {
                onError.onError(source, new IOException("Cannot move \"" + sourceName + "\" into itself"));
                continue;
            }

            try {
                // A symlink to a directory must be moved as the link node itself — isDirectory()
                // follows the link, and recursing through it would move the target's contents and
                // leave the linked directory drained.
                boolean sourceIsLink = Files.isSymbolicLink(source);
                if (Files.isDirectory(source) && !sourceIsLink) {
                    // Enumerate every entry up-front and move them one by one so each file ticks
                    // the progress bar — a single Files.move on a directory only reported one step
                    // regardless of size.
                    MoveDirResult result = moveDirectoryWithProgress(source, target, movedCount, holder,
                        onProgress, onOverwriteConfirm, onError, isCancelled);
                    movedCount = result.movedCount();
                    if (result.cancelled()) {
                        break;
                    }
                }
                else {
                    TargetAction action = moveFileWithOverwrite(source, target, holder, onOverwriteConfirm);
                    if (action == TargetAction.CANCEL) {
                        break;
                    }
                    if (action == TargetAction.OVERWRITE) {
                        movedCount++;
                        onProgress.onProgress(movedCount, sourceName);
                    }
                }
            }
            catch (Exception e) {
                LOG.warn("Failed to move " + source + " to " + destination, e);
                onError.onError(source, e);
            }
        }

        invalidateDestinationTree(destination, sources);
        invalidateParents(sources);
    }

    /** Moves one file honouring the policy, with the same return contract as the copy path. */
    private TargetAction moveFileWithOverwrite(Path source, Path target, PolicyHolder holder,
                                               OverwriteConfirmation onOverwriteConfirm) throws IOException {
        if (!Files.exists(target)) {
            crossFileSystemMove(source, target);
            return TargetAction.OVERWRITE;
        }

        TargetAction action = resolveOverwriteAction(source, target, holder, onOverwriteConfirm);
        if (action == TargetAction.OVERWRITE) {
            crossFileSystemMove(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return action;
    }

    private MoveDirResult moveDirectoryWithProgress(Path source, Path target, int startCount, PolicyHolder holder,
                                                    ProgressListener onProgress,
                                                    OverwriteConfirmation onOverwriteConfirm,
                                                    ErrorListener onError, BooleanSupplier isCancelled) {
        int movedCount = startCount;
        boolean leftBehind = false;

        try {
            // Same as the copy path: only an actually-created directory counts as work.
            boolean alreadyExists = Files.isDirectory(target);
            Files.createDirectories(target);
            if (!alreadyExists) {
                movedCount++;
                onProgress.onProgress(movedCount, source.getFileName().toString());
            }
        }
        catch (Exception e) {
            LOG.warn("Failed to create directory " + target, e);
            onError.onError(source, e);
            return new MoveDirResult(movedCount, false, true);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (Path entry : stream) {
                if (isCancelled.getAsBoolean()) {
                    return new MoveDirResult(movedCount, true, true);
                }

                String entryName = entry.getFileName().toString();
                Path entryTarget = target.resolve(entryName);
                // Same symlink guard as the top-level loop: recurse only into real directories,
                // move link nodes as files.
                boolean entryIsLink = Files.isSymbolicLink(entry);

                if (Files.isDirectory(entry) && !entryIsLink) {
                    MoveDirResult sub = moveDirectoryWithProgress(entry, entryTarget, movedCount, holder,
                        onProgress, onOverwriteConfirm, onError, isCancelled);
                    movedCount = sub.movedCount();
                    if (sub.leftBehind()) {
                        leftBehind = true;
                    }
                    if (sub.cancelled()) {
                        return new MoveDirResult(movedCount, true, true);
                    }
                }
                else {
                    try {
                        TargetAction action = moveFileWithOverwrite(entry, entryTarget, holder, onOverwriteConfirm);
                        if (action == TargetAction.CANCEL) {
                            return new MoveDirResult(movedCount, true, true);
                        }
                        if (action == TargetAction.OVERWRITE) {
                            movedCount++;
                            onProgress.onProgress(movedCount, entryName);
                        }
                        else {
                            leftBehind = true;
                        }
                    }
                    catch (Exception e) {
                        LOG.warn("Failed to move " + entry, e);
                        onError.onError(entry, e);
                        leftBehind = true;
                    }
                }
            }
        }
        catch (Exception e) {
            LOG.warn("Failed to list directory " + source, e);
            onError.onError(source, e);
            return new MoveDirResult(movedCount, false, true);
        }

        try {
            Files.deleteIfExists(source);
        }
        catch (Exception e) {
            if (!(e instanceof DirectoryNotEmptyException) || !leftBehind) {
                LOG.warn("Failed to remove source directory " + source, e);
                onError.onError(source, e);
            }
            leftBehind = true;
        }

        return new MoveDirResult(movedCount, false, leftBehind);
    }

    // --- Delete ---------------------------------------------------------------------------------

    public void deleteFilesWithProgress(List<Path> paths, ProgressListener onProgress, ErrorListener onError,
                                        BooleanSupplier isCancelled, boolean useRecycleBin) {
        int deletedCount = 0;

        if (useRecycleBin) {
            // The UI checks availability before showing the recycle-bin dialog; if support vanished
            // since (or a caller skipped that check), refuse rather than silently downgrading the
            // reversible operation the user confirmed into a permanent delete.
            if (!myMoveToTrashSupported.getAsBoolean()) {
                for (Path path : paths) {
                    onError.onError(path, new IOException("Recycle Bin is not available on this system"));
                }
                return;
            }

            // Directories move atomically (the OS handles recursion), so the tree isn't walked here
            // and progress ticks once per top-level path.
            for (Path path : paths) {
                if (isCancelled.getAsBoolean()) {
                    break;
                }
                try {
                    if (myMoveToTrash.test(path)) {
                        deletedCount++;
                        onProgress.onProgress(deletedCount, path.getFileName().toString());
                    }
                    else {
                        onError.onError(path, new IOException("Failed to move to Recycle Bin: " + path));
                    }
                }
                catch (Exception e) {
                    LOG.warn("Failed to move to trash " + path, e);
                    onError.onError(path, e);
                }
            }
            invalidateParents(paths);
            return;
        }

        for (Path path : paths) {
            if (isCancelled.getAsBoolean()) {
                break;
            }
            try {
                if (Files.isDirectory(path)) {
                    deletedCount = deleteTree(path, deletedCount, onProgress, onError, isCancelled);
                }
                else {
                    try {
                        Files.deleteIfExists(path);
                        deletedCount++;
                        onProgress.onProgress(deletedCount, path.getFileName().toString());
                    }
                    catch (Exception e) {
                        LOG.warn("Failed to delete " + path, e);
                        onError.onError(path, e);
                    }
                }
            }
            catch (Exception e) {
                LOG.warn("Failed to process " + path, e);
                onError.onError(path, e);
            }
        }

        invalidateParents(paths);
    }

    private int deleteTree(Path path, int startCount, ProgressListener onProgress, ErrorListener onError,
                           BooleanSupplier isCancelled) throws IOException {
        // Collect the tree first so the visitor callbacks stay free of progress/error reporting.
        List<Path> entries = new ArrayList<>();
        List<Path> walkErrorPaths = new ArrayList<>();
        List<IOException> walkErrors = new ArrayList<>();

        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                entries.add(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                walkErrorPaths.add(file);
                walkErrors.add(exc);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) {
                if (exc != null) {
                    walkErrorPaths.add(dir);
                    walkErrors.add(exc);
                }
                entries.add(dir);
                return FileVisitResult.CONTINUE;
            }
        });

        for (int i = 0; i < walkErrorPaths.size(); i++) {
            onError.onError(walkErrorPaths.get(i), walkErrors.get(i));
        }

        int deletedCount = startCount;
        for (Path entry : entries) {
            if (isCancelled.getAsBoolean()) {
                break;
            }
            try {
                Files.delete(entry);
                deletedCount++;
                onProgress.onProgress(deletedCount, entry.getFileName().toString());
            }
            catch (Exception e) {
                LOG.warn("Failed to delete " + entry, e);
                onError.onError(entry, e);
            }
        }
        return deletedCount;
    }

    // --- Single-shot operations -----------------------------------------------------------------

    public Path renameFile(Path source, String newName) throws IOException {
        Path parent = source.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Cannot rename a root path");
        }
        Path moved = Files.move(source, parent.resolve(newName));
        invalidateListingCache(parent);
        return moved;
    }

    public Path createDirectory(Path parent, String name) throws IOException {
        Path created = Files.createDirectory(parent.resolve(name));
        invalidateListingCache(parent);
        return created;
    }

    /**
     * Creates a symbolic link pointing at {@code target}, which may be absolute or relative and need
     * not exist — a link to a missing target is valid and renders as broken. Note that on Windows
     * this requires Developer Mode or elevation.
     */
    public Path createSymbolicLink(Path link, Path target) throws IOException {
        Path parent = link.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path created = Files.createSymbolicLink(link, target);
        invalidateListingCache(parent != null ? parent : link);
        return created;
    }

    /**
     * Creates a hard link referencing the existing file {@code target} — a second directory entry
     * for the same data, both names equal peers. Unlike a symbolic link the target must exist, must
     * be a regular file (most filesystems forbid hard-linking directories), and must live on the
     * same filesystem as the link.
     */
    public Path createHardLink(Path link, Path target) throws IOException {
        Path parent = link.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path created = Files.createLink(link, target);
        invalidateListingCache(parent != null ? parent : link);
        return created;
    }

    // --- Drive roots ----------------------------------------------------------------------------

    /** Most recent root list produced by the shared poller; null before the first scan. */
    public @Nullable List<String> currentRoots() {
        return myCachedRoots;
    }

    /**
     * Subscribes to drive-root updates. The listener is invoked with the cached list immediately (on
     * the caller's thread) when one exists, then on the UI thread whenever the list changes. The
     * first subscriber starts the shared poller; the returned handle unsubscribes, and the poller
     * stops when the last subscriber leaves.
     */
    public Runnable subscribeToRoots(Consumer<List<String>> listener) {
        synchronized (myRootsListeners) {
            myRootsListeners.add(listener);
            if (myRootsPollHandle == null) {
                myRootsPollHandle = myRootsScheduler.scheduleWithFixedDelay(this::pollRootsOnce,
                    0, ROOTS_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
            }
        }

        List<String> cached = myCachedRoots;
        if (cached != null) {
            listener.accept(cached);
        }

        return () -> {
            synchronized (myRootsListeners) {
                myRootsListeners.remove(listener);
                if (myRootsListeners.isEmpty() && myRootsPollHandle != null) {
                    myRootsPollHandle.cancel(false);
                    myRootsPollHandle = null;
                }
            }
        };
    }

    private void pollRootsOnce() {
        List<String> roots;
        try {
            roots = getRoots();
            // Resolve Windows drive labels here so the UI renderer only ever hits the cache
            // (getSystemDisplayName is a blocking shell call).
            for (String root : roots) {
                DriveLabels.getDisplayText(root);
            }
        }
        catch (Exception e) {
            LOG.debug("Drive roots scan failed", e);
            return;
        }

        if (roots.equals(myCachedRoots)) {
            return;
        }
        myCachedRoots = roots;

        List<Consumer<List<String>>> snapshot = List.copyOf(myRootsListeners);
        Application.get().invokeLater(() -> {
            for (Consumer<List<String>> listener : snapshot) {
                listener.accept(roots);
            }
        });
    }

    public List<String> getRoots() {
        List<String> roots = new ArrayList<>();

        if (myWindows) {
            for (File root : File.listRoots()) {
                roots.add(root.getAbsolutePath());
            }
            addHome(roots);
            return roots;
        }

        roots.add("/");
        addHome(roots);

        if (myMac) {
            addSubdirectories(roots, Path.of("/Volumes"));
        }
        else {
            addSubdirectories(roots, Path.of("/media"));
            addSubdirectories(roots, Path.of("/mnt"));
            String user = System.getProperty("user.name");
            if (user != null) {
                addSubdirectories(roots, Path.of("/run/media/" + user));
            }
        }
        return roots;
    }

    @Override
    public void dispose() {
        myRootsScheduler.shutdownNow();
    }

    // --- Move/copy internals --------------------------------------------------------------------

    void crossFileSystemMove(Path source, Path target, CopyOption... options) throws IOException {
        if (source.getFileSystem() == target.getFileSystem()) {
            // A lazy temp dir lives on the default filesystem, so moving an entry out of an open
            // archive takes this branch — pull the real bytes onto the stubs first, or the
            // destination would receive zero-filled placeholders. A no-op outside any archive.
            OpenVfsRegistry.materializeTreeIfNeeded(source);
            Files.move(source, target, options);
            return;
        }

        boolean replaceExisting = containsReplaceExisting(options);

        if (Files.isSymbolicLink(source)) {
            // Recreate the link node on the target side rather than following it: isDirectory() on
            // a directory link would otherwise route it through the recursive copy+delete below,
            // duplicating the target's content and then deleting it.
            if (replaceExisting) {
                Files.deleteIfExists(target);
            }
            Files.copy(source, target, LinkOption.NOFOLLOW_LINKS);
            Files.delete(source);
            return;
        }

        if (Files.isDirectory(source)) {
            // Same-filesystem move with REPLACE_EXISTING replaces atomically (or fails on a
            // non-empty target). copyDirectoryRecursive alone would merge into an existing target,
            // leaving stale entries the user expected to be replaced. Wipe the target first so
            // cross-filesystem semantics line up with the same-filesystem path.
            if (replaceExisting && Files.exists(target)) {
                deleteDirectoryRecursive(target);
            }
            copyDirectoryRecursive(source, target);
            deleteDirectoryRecursive(source);
            return;
        }

        OpenVfsRegistry.materializeIfNeeded(source);
        if (replaceExisting) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        else {
            Files.copy(source, target);
        }
        Files.delete(source);
    }

    private static void copyDirectoryRecursive(Path source, Path target) throws IOException {
        List<Path> failurePaths = new ArrayList<>();
        List<IOException> failures = new ArrayList<>();

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String relativePath = source.relativize(dir).toString();
                Path targetDir = relativePath.isEmpty() ? target : target.resolve(relativePath);
                try {
                    Files.createDirectories(targetDir);
                }
                catch (IOException e) {
                    failurePaths.add(dir);
                    failures.add(e);
                    LOG.warn("Failed to create directory " + targetDir, e);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    OpenVfsRegistry.materializeIfNeeded(file);
                    Files.copy(file, target.resolve(source.relativize(file).toString()),
                        StandardCopyOption.REPLACE_EXISTING);
                }
                catch (IOException e) {
                    failurePaths.add(file);
                    failures.add(e);
                    LOG.warn("Failed to copy " + file, e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                failurePaths.add(file);
                failures.add(exc);
                LOG.warn("Failed to copy " + file, exc);
                return FileVisitResult.CONTINUE;
            }
        });

        if (!failures.isEmpty()) {
            IOException first = failures.get(0);
            throw new IOException("Failed to copy " + failures.size() + " item(s) under " + source
                + ", including " + failurePaths.get(0) + ": " + first.getMessage(), first);
        }
    }

    private static void deleteDirectoryRecursive(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                LOG.warn("Failed to delete " + file, exc);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) {
                if (exc != null) {
                    LOG.warn("Failed to fully iterate " + dir, exc);
                }
                try {
                    Files.delete(dir);
                }
                catch (Exception e) {
                    LOG.warn("Failed to delete directory " + dir, e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // --- Small helpers --------------------------------------------------------------------------

    /**
     * On Windows some system junctions like {@code C:\Documents and Settings} have an ACL denying
     * listing, but they are reparse points aimed at an accessible target ({@code C:\Users}). Follow
     * the reparse point so the user can still enter them.
     */
    private Path resolveReparsePoint(Path directory) {
        if (!myWindows) {
            return directory;
        }
        try {
            return directory.toRealPath();
        }
        catch (Exception e) {
            return directory;
        }
    }

    private void invalidateParents(List<Path> paths) {
        Set<Path> parents = new HashSet<>();
        for (Path path : paths) {
            Path parent = path.getParent();
            if (parent != null) {
                parents.add(parent);
            }
        }
        for (Path parent : parents) {
            invalidateListingCache(parent);
        }
    }

    private static void addHome(List<String> roots) {
        String home = System.getProperty("user.home");
        if (home == null) {
            return;
        }
        Path homePath = Path.of(home);
        if (Files.exists(homePath) && Files.isDirectory(homePath)) {
            roots.add(homePath.toString());
        }
    }

    private static void addSubdirectories(List<String> roots, Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    roots.add(child.toString());
                }
            }
        }
        catch (Exception e) {
            LOG.debug("Cannot list mount point directory " + dir, e);
        }
    }

    private static String dosAttrsToString(DosFileAttributes attrs) {
        StringBuilder sb = new StringBuilder(4);
        if (attrs.isReadOnly()) {
            sb.append('R');
        }
        if (attrs.isHidden()) {
            sb.append('H');
        }
        if (attrs.isSystem()) {
            sb.append('S');
        }
        if (attrs.isArchive()) {
            sb.append('A');
        }
        return sb.toString();
    }

    private static CopyOption[] withReplaceExisting(CopyOption[] options) {
        CopyOption[] combined = new CopyOption[options.length + 1];
        combined[0] = StandardCopyOption.REPLACE_EXISTING;
        System.arraycopy(options, 0, combined, 1, options.length);
        return combined;
    }

    private static boolean containsReplaceExisting(CopyOption[] options) {
        for (CopyOption option : options) {
            if (option == StandardCopyOption.REPLACE_EXISTING) {
                return true;
            }
        }
        return false;
    }

    // --- Test seams -----------------------------------------------------------------------------

    void setClockForTesting(LongSupplier clock) {
        myClock = clock;
    }

    void setMoveToTrashForTesting(Predicate<Path> moveToTrash) {
        myMoveToTrash = moveToTrash;
    }

    void setMoveToTrashSupportedForTesting(BooleanSupplier supported) {
        myMoveToTrashSupported = supported;
    }
}
