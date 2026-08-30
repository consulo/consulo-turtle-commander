package consulo.turtlecommander.search;

import consulo.turtlecommander.util.PermissionFlag;

import java.util.Set;

public record PermissionsFilter(PermissionsFilterMode mode, Set<PermissionFlag> flags) {
}
