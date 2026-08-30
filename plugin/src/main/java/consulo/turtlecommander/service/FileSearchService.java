package consulo.turtlecommander.service;

import consulo.application.util.SystemInfo;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.search.DateFilter;
import consulo.turtlecommander.search.FileSearchCriteria;
import consulo.turtlecommander.search.PermissionsFilter;
import consulo.turtlecommander.search.SizeFilter;
import consulo.turtlecommander.util.FileAttributes;
import consulo.turtlecommander.util.FileUtils;
import consulo.turtlecommander.util.PermissionFlag;
import consulo.turtlecommander.vfs.OpenVfsRegistry;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Walks a directory tree applying the search criteria. Blocking — callers run it off the UI thread
 * and drive cancellation through the supplied predicate.
 */
public class FileSearchService {
    private static final long PAUSE_POLL_NANOS = 10_000_000L;
    private static final int PROGRESS_EVERY = 50;

    public static class InvalidPatternException extends RuntimeException {
        public InvalidPatternException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int scannedCount, String currentDir);
    }

    private final FileSearchCriteria myCriteria;
    private final boolean myWindows;
    private final Function<Path, String> myOwnerReader;
    private final Function<Path, String> myGroupReader;
    private final Function<Path, Set<PermissionFlag>> myPermissionsFlagReader;

    private volatile boolean myPaused;

    public FileSearchService(FileSearchCriteria criteria) {
        this(criteria, SystemInfo.isWindows, FileAttributes::readOwner, FileAttributes::readGroup, null);
    }

    public FileSearchService(FileSearchCriteria criteria, boolean windows,
                             Function<Path, String> ownerReader,
                             Function<Path, String> groupReader,
                             @Nullable Function<Path, Set<PermissionFlag>> permissionsFlagReader) {
        myCriteria = criteria;
        myWindows = windows;
        myOwnerReader = ownerReader;
        myGroupReader = groupReader;
        myPermissionsFlagReader = permissionsFlagReader != null
            ? permissionsFlagReader
            : path -> FileAttributes.readPermissionFlags(path, windows);
    }

    public void setPaused(boolean paused) {
        myPaused = paused;
    }

    public boolean isPaused() {
        return myPaused;
    }

    public void search(Consumer<FileEntry> onResult, BooleanSupplier isCancelled, ProgressCallback onProgress)
        throws IOException {

        Predicate<String> nameMatcher = buildNameMatcher();
        int[] scannedCount = {0};

        Files.walkFileTree(myCriteria.rootPath(), new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (isCancelled.getAsBoolean()) {
                    return FileVisitResult.TERMINATE;
                }
                waitWhilePaused(isCancelled);
                scannedCount[0]++;
                if (scannedCount[0] % PROGRESS_EVERY == 0) {
                    onProgress.onProgress(scannedCount[0], dir.toString());
                }
                if (!dir.equals(myCriteria.rootPath())) {
                    checkAndReport(dir, attrs, nameMatcher, onResult);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isCancelled.getAsBoolean()) {
                    return FileVisitResult.TERMINATE;
                }
                waitWhilePaused(isCancelled);
                scannedCount[0]++;
                if (scannedCount[0] % PROGRESS_EVERY == 0) {
                    Path parent = file.getParent();
                    onProgress.onProgress(scannedCount[0], parent != null ? parent.toString() : "");
                }
                checkAndReport(file, attrs, nameMatcher, onResult);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return isCancelled.getAsBoolean() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) {
                return isCancelled.getAsBoolean() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }
        });

        onProgress.onProgress(scannedCount[0], "");
    }

    private @Nullable Predicate<String> buildNameMatcher() {
        String pattern = myCriteria.namePattern();
        if (pattern == null) {
            return null;
        }

        boolean caseSensitive = myCriteria.caseSensitive();
        switch (myCriteria.namePatternMode()) {
            case GLOB -> {
                String wrapped = FileUtils.wrapAsSubstringGlobIfPlain(pattern);
                String effective = caseSensitive ? wrapped : wrapped.toLowerCase(Locale.ROOT);
                PathMatcher pathMatcher;
                try {
                    pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + effective);
                }
                catch (Exception e) {
                    throw new InvalidPatternException("Invalid glob pattern: " + pattern, e);
                }
                return name -> pathMatcher.matches(
                    Path.of(caseSensitive ? name : name.toLowerCase(Locale.ROOT)));
            }
            case REGEXP -> {
                Pattern compiled;
                try {
                    compiled = Pattern.compile(pattern, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
                }
                catch (PatternSyntaxException e) {
                    throw new InvalidPatternException("Invalid regex pattern: " + pattern, e);
                }
                return name -> compiled.matcher(name).find();
            }
            default -> {
                return null;
            }
        }
    }

    private void checkAndReport(Path path, BasicFileAttributes attrs, @Nullable Predicate<String> nameMatcher,
                                Consumer<FileEntry> onResult) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return;
        }
        String name = fileName.toString();

        switch (myCriteria.resultKind()) {
            case FILES -> {
                if (attrs.isDirectory()) {
                    return;
                }
            }
            case DIRS -> {
                if (!attrs.isDirectory()) {
                    return;
                }
            }
            case ALL -> {
                // no filtering
            }
        }

        // walkFileTree doesn't follow links, so a symlink arrives with NOFOLLOW attrs. A link is
        // broken when following it can't find the target.
        boolean isLink = attrs.isSymbolicLink();
        switch (myCriteria.symlinkFilter()) {
            case ONLY -> {
                if (!isLink) {
                    return;
                }
            }
            case EXCLUDE -> {
                if (isLink) {
                    return;
                }
            }
            case ANY -> {
                // no filtering
            }
        }

        if (nameMatcher != null && !nameMatcher.test(name)) {
            return;
        }

        SizeFilter sizeFilter = myCriteria.sizeFilter();
        if (sizeFilter != null) {
            if (attrs.isDirectory() || !matchesSize(attrs.size(), sizeFilter)) {
                return;
            }
        }

        DateFilter creationFilter = myCriteria.creationDateFilter();
        if (creationFilter != null && !matchesDate(attrs.creationTime().toMillis(), creationFilter)) {
            return;
        }

        DateFilter modificationFilter = myCriteria.modificationDateFilter();
        if (modificationFilter != null && !matchesDate(attrs.lastModifiedTime().toMillis(), modificationFilter)) {
            return;
        }

        String ownerPattern = myCriteria.ownerPattern();
        String groupPattern = myCriteria.groupPattern();
        String owner = ownerPattern != null ? myOwnerReader.apply(path) : "";
        String group = groupPattern != null && !myWindows ? myGroupReader.apply(path) : "";

        if (ownerPattern != null && !StringUtil.containsIgnoreCase(owner, ownerPattern)) {
            return;
        }
        if (groupPattern != null && !StringUtil.containsIgnoreCase(group, groupPattern)) {
            return;
        }

        PermissionsFilter permissionsFilter = myCriteria.permissionsFilter();
        if (permissionsFilter != null && !matchesPermissions(myPermissionsFlagReader.apply(path), permissionsFilter)) {
            return;
        }

        String contentPattern = myCriteria.contentPattern();
        if (contentPattern != null) {
            if (attrs.isDirectory() || !matchesContent(path, contentPattern, myCriteria.contentCaseSensitive())) {
                return;
            }
        }

        String linkTarget = null;
        if (isLink) {
            try {
                linkTarget = Files.readSymbolicLink(path).toString();
            }
            catch (Exception e) {
                linkTarget = null;
            }
        }

        onResult.accept(FileEntry.builder(name, path)
            .directory(attrs.isDirectory())
            .size(attrs.size())
            .creationTime(attrs.creationTime())
            .lastModified(attrs.lastModifiedTime())
            .owner(owner)
            .group(group)
            .permissions(FileAttributes.readPermissions(path, myWindows))
            .symbolicLink(isLink)
            .brokenSymlink(isLink && !Files.exists(path))
            .linkTarget(linkTarget)
            .namedPipe(!myWindows && !isLink && attrs.isOther() && FileAttributes.isNamedPipe(path))
            .build());
    }

    private static boolean matchesSize(long size, SizeFilter filter) {
        return switch (filter.mode()) {
            case MORE_THAN -> size > filter.sizeBytes();
            case LESS_THAN -> size < filter.sizeBytes();
            case APPROX_EQUAL -> {
                long tolerance = Math.max(1L, (long)(filter.sizeBytes() * 0.1));
                yield Math.abs(size - filter.sizeBytes()) <= tolerance;
            }
            case IN_BETWEEN -> {
                long other = filter.sizeBytes2() != null ? filter.sizeBytes2() : filter.sizeBytes();
                yield size >= Math.min(filter.sizeBytes(), other) && size <= Math.max(filter.sizeBytes(), other);
            }
        };
    }

    private static boolean matchesDate(long millis, DateFilter filter) {
        return switch (filter.mode()) {
            case EARLIER -> millis < filter.startMillis();
            case LATER -> millis > filter.endMillis();
            case APPROX_EQUAL -> millis >= filter.startMillis() && millis <= filter.endMillis();
            case IN_BETWEEN -> {
                long low = Math.min(filter.startMillis(),
                    filter.startMillis2() != null ? filter.startMillis2() : filter.startMillis());
                long high = Math.max(filter.endMillis(),
                    filter.endMillis2() != null ? filter.endMillis2() : filter.endMillis());
                yield millis >= low && millis <= high;
            }
        };
    }

    private static boolean matchesContent(Path path, String pattern, boolean caseSensitive) {
        // Stream the bytes from the source archive if this is a lazy stub; a no-op for regular
        // files and for the filesystems that extract eagerly.
        OpenVfsRegistry.materializeIfNeeded(path);

        // Skip files the IDE classifies as binary up front: cheaper and more accurate than
        // inferring it from a decode failure, which both over-skips valid non-UTF-8 text and
        // needlessly scans UTF-8-clean binaries. Guarded so headless callers fall back to the
        // decode-based heuristic below.
        try {
            Path fileName = path.getFileName();
            if (fileName != null && FileTypeRegistry.getInstance().getFileTypeByFileName(fileName.toString()).isBinary()) {
                return false;
            }
        }
        catch (Exception e) {
            // No platform application available — fall through to the decode heuristic.
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (caseSensitive ? line.contains(pattern) : StringUtil.containsIgnoreCase(line, pattern)) {
                    return true;
                }
            }
        }
        catch (MalformedInputException e) {
            // binary file, skip
        }
        catch (IOException e) {
            // unreadable, skip
        }
        return false;
    }

    private static boolean matchesPermissions(Set<PermissionFlag> fileFlags, PermissionsFilter filter) {
        return switch (filter.mode()) {
            case EXACT_MATCH -> fileFlags.equals(filter.flags());
            case ALL_OF -> fileFlags.containsAll(filter.flags());
            case ANY_OF -> {
                if (filter.flags().isEmpty()) {
                    yield true;
                }
                for (PermissionFlag flag : filter.flags()) {
                    if (fileFlags.contains(flag)) {
                        yield true;
                    }
                }
                yield false;
            }
            case NONE_OF -> {
                for (PermissionFlag flag : filter.flags()) {
                    if (fileFlags.contains(flag)) {
                        yield false;
                    }
                }
                yield true;
            }
        };
    }


    private void waitWhilePaused(BooleanSupplier isCancelled) {
        while (myPaused && !isCancelled.getAsBoolean()) {
            LockSupport.parkNanos(PAUSE_POLL_NANOS);
        }
    }
}
