package consulo.turtlecommander.ui;

import consulo.platform.base.icon.PlatformIconGroup;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.settings.ResolvedStyle;
import consulo.turtlecommander.settings.RuleIcons;
import consulo.turtlecommander.vfs.VirtualFileSystemRegistry;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import org.jspecify.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Color;

/** Chooses the icon for a listing row, including rule overrides, dots and the symlink badge. */
public final class FileEntryIconUtil {
    private FileEntryIconUtil() {
    }

    public static Icon iconFor(FileEntry entry, boolean enableHighlighting, ResolvedStyle resolved) {
        return TargetAWT.to(imageFor(entry, enableHighlighting, resolved));
    }

    public static Image imageFor(FileEntry entry, boolean enableHighlighting, ResolvedStyle resolved) {
        boolean styleable = enableHighlighting && !entry.parentLink();

        // A rule may replace the type/folder icon outright. The "..", non-highlighted and
        // unknown-key cases all fall through to the defaults below.
        Image override = styleable ? RuleIcons.resolve(resolved.iconId()) : null;
        Image base = baseImage(entry, override);

        // Overlay the rule's coloured dot on whatever icon was chosen, file or folder alike. The
        // parent link is exempt — it never carries rule styling.
        if (styleable) {
            Color dot = FileStyleUtil.iconDotColorOf(resolved);
            if (dot != null) {
                return DirectoryIcons.iconWithDot(base, dot);
            }
        }

        // Badge symlinks with the platform link arrow so they read as links in every view; colour
        // (from the built-in valid/broken rules) conveys whether the target resolves.
        if (entry.symbolicLink()) {
            return ImageEffects.layered(base, PlatformIconGroup.nodesSymlink());
        }
        return base;
    }

    private static Image baseImage(FileEntry entry, @Nullable Image override) {
        if (entry.parentLink()) {
            return PlatformIconGroup.nodesUplevel();
        }
        if (override != null) {
            return override;
        }
        if (entry.directory()) {
            return PlatformIconGroup.nodesFolder();
        }
        if (VirtualFileSystemRegistry.supportsByExtension(entry.name())) {
            return PlatformIconGroup.filetypesArchive();
        }

        Image typeIcon = FileTypeRegistry.getInstance().getFileTypeByFileName(entry.name()).getIcon();
        return typeIcon != null ? typeIcon : PlatformIconGroup.filetypesAny_type();
    }
}
