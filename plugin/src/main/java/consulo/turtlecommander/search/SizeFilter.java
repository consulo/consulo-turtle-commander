package consulo.turtlecommander.search;

import org.jspecify.annotations.Nullable;

/** @param sizeBytes2 the upper bound for {@link SizeFilterMode#IN_BETWEEN}; null otherwise */
public record SizeFilter(SizeFilterMode mode, long sizeBytes, @Nullable Long sizeBytes2) {
}
