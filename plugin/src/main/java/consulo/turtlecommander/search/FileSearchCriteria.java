package consulo.turtlecommander.search;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/** Everything the search dialog collects, in the form the search service consumes. */
public record FileSearchCriteria(
    Path rootPath,
    @Nullable String namePattern,
    NamePatternMode namePatternMode,
    boolean caseSensitive,
    ResultKind resultKind,
    @Nullable String contentPattern,
    boolean contentCaseSensitive,
    @Nullable SizeFilter sizeFilter,
    @Nullable DateFilter creationDateFilter,
    @Nullable DateFilter modificationDateFilter,
    @Nullable String ownerPattern,
    @Nullable String groupPattern,
    @Nullable PermissionsFilter permissionsFilter,
    SymlinkSearchFilter symlinkFilter
) {
}
