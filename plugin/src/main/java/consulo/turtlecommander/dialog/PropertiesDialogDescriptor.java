package consulo.turtlecommander.dialog;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.util.DateTimeFormatters;
import consulo.turtlecommander.util.FileUtils;
import consulo.turtlecommander.util.UiTask;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.LabelStyle;
import consulo.ui.Space;
import consulo.ui.TextBox;
import consulo.ui.TextBoxStyle;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.image.Image;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicBoolean;
import consulo.ui.WidthAndHeight;

/**
 * Cross-platform fallback Properties dialog, shown when no native one is available - an unknown Linux desktop, or a
 * path that is no real filesystem entry (inside an archive). Read-only by design.
 *
 * <p>The values are borderless read-only text boxes rather than labels, so they can be selected and copied.
 *
 * <p>A directory's recursive size and counts, and the access time, are read on a pooled thread - on a dead network
 * path even one stat can block for the SMB timeout. Closing the dialog stops the walk.
 */
public final class PropertiesDialogDescriptor extends DialogDescriptor {
    private final FileEntry myEntry;
    private final AtomicBoolean myCancelled = new AtomicBoolean(false);

    public PropertiesDialogDescriptor(FileEntry entry) {
        super(LocalizeValue.localizeTODO("Properties"));
        myEntry = entry;
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        Disposer.register(uiDisposable, () -> myCancelled.set(true));

        TextBox sizeBox = copyableBox(initialSizeText());
        TextBox accessedBox = copyableBox("…");

        Component form = FormBuilder.create()
            .addLabeled(LocalizeValue.localizeTODO("Type:"), copyableBox(typeDescription()))
            .addLabeled(LocalizeValue.localizeTODO("Path:"), copyableBox(myEntry.path().toAbsolutePath().toString()))
            .addLabeled(LocalizeValue.localizeTODO("Location:"), copyableBox(locationText()))
            .addLabeled(LocalizeValue.localizeTODO("Size:"), sizeBox)
            .addLabeled(LocalizeValue.localizeTODO("Created:"), copyableBox(formatTime(toMillis(myEntry.creationTime()))))
            .addLabeled(LocalizeValue.localizeTODO("Modified:"), copyableBox(formatTime(toMillis(myEntry.lastModified()))))
            .addLabeled(LocalizeValue.localizeTODO("Accessed:"), accessedBox)
            .addLabeled(LocalizeValue.localizeTODO("Owner:"), copyableBox(orDash(myEntry.owner())))
            .addLabeled(LocalizeValue.localizeTODO("Group:"), copyableBox(orDash(myEntry.group())))
            .addLabeled(LocalizeValue.localizeTODO("Permissions:"), copyableBox(orDash(myEntry.permissions())))
            .build();

        VerticalLayout layout = VerticalLayout.create(Space.LARGE);
        layout.add(createHeader());
        layout.add(form);

        UIAccess uiAccess = UIAccess.current();
        if (myEntry.directory()) {
            UiTask.background(() -> {
                String text = computeDirSizeText();
                if (!myCancelled.get()) {
                    uiAccess.give(() -> setIfOpen(sizeBox, text));
                }
            });
        }
        UiTask.background(() -> {
            String text = formatTime(readAccessTime(myEntry.path()));
            uiAccess.give(() -> setIfOpen(accessedBox, text));
        });
        return layout;
    }

    @RequiredUIAccess
    private void setIfOpen(TextBox box, String text) {
        if (!myCancelled.get()) {
            box.setValue(text);
        }
    }

    /**
     * The name is a bold label beside the icon; it is the only value that cannot be selected, and the path row
     * carries it anyway.
     */
    @RequiredUIAccess
    private Component createHeader() {
        Label nameLabel = Label.create(LocalizeValue.of(myEntry.name()));
        nameLabel.setImage(myEntry.directory() ? PlatformIconGroup.nodesFolder() : fileTypeIcon());
        nameLabel.addStyle(LabelStyle.FONT_BOLD);
        nameLabel.addStyle(LabelStyle.FONT_LARGE);
        return nameLabel;
    }

    /** Nothing to confirm - the one button only closes. */
    /** Wide enough for a path of some depth; sized to the content, it cut the path and location rows short. */
    @Override
    public @Nullable WidthAndHeight getInitialSize() {
        // in font sizes: eleven rows of about two, and the header and the buttons
        return WidthAndHeight.ofFont(50, 32);
    }

    @Override
    public AnAction[] createActions(boolean inverseOrder) {
        return new AnAction[]{new DialogOkAction(LocalizeValue.localizeTODO("Close"))};
    }

    @RequiredUIAccess
    private static TextBox copyableBox(String text) {
        TextBox box = TextBox.create(text);
        box.setEditable(false);
        // no border, so the value reads as a label
        box.addStyle(TextBoxStyle.INPLACE);
        return box;
    }

    private Image fileTypeIcon() {
        Image icon = FileTypeRegistry.getInstance().getFileTypeByFileName(myEntry.name()).getIcon();
        return icon != null ? icon : PlatformIconGroup.filetypesAny_type();
    }

    private String typeDescription() {
        if (myEntry.directory()) {
            return "Directory";
        }
        if (myEntry.namedPipe()) {
            return "Named pipe (FIFO)";
        }
        FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(myEntry.name());
        // the unknown type's description is just "Unknown" - a name made from the extension says more
        if ("UNKNOWN".equalsIgnoreCase(type.getId())) {
            String extension = StringUtil.substringAfterLast(myEntry.name(), ".");
            return StringUtil.isEmpty(extension) ? "File" : StringUtil.toUpperCase(extension) + " File";
        }
        String description = type.getDescription().get();
        return description.isEmpty() ? type.getId() : description;
    }

    private String locationText() {
        Path parent = myEntry.path().toAbsolutePath().getParent();
        return parent != null ? parent.toString() : myEntry.path().toAbsolutePath().toString();
    }

    private String initialSizeText() {
        if (myEntry.directory()) {
            return "Calculating…";
        }
        return FileUtils.formatSize(myEntry.size()) + " (" + String.format("%,d", myEntry.size()) + " bytes)";
    }

    private String computeDirSizeText() {
        DirSizeVisitor visitor = new DirSizeVisitor();
        try {
            Files.walkFileTree(myEntry.path(), visitor);
        }
        catch (Exception e) {
            // permission denied on the root, or the path is gone - show what was counted
        }
        return FileUtils.formatSize(visitor.myBytes) + " (" + String.format("%,d", visitor.myBytes)
            + " bytes), " + visitor.myFiles + " files, " + visitor.myDirs + " folders";
    }

    private final class DirSizeVisitor extends SimpleFileVisitor<Path> {
        private long myBytes;
        private long myFiles;
        private long myDirs;

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (myCancelled.get()) {
                return FileVisitResult.TERMINATE;
            }
            myFiles++;
            myBytes += attrs.size();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (myCancelled.get()) {
                return FileVisitResult.TERMINATE;
            }
            if (!dir.equals(myEntry.path())) {
                myDirs++;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            return FileVisitResult.CONTINUE;
        }
    }

    private static @Nullable Long readAccessTime(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class).lastAccessTime().toMillis();
        }
        catch (Exception e) {
            return null;
        }
    }

    private static @Nullable Long toMillis(@Nullable FileTime time) {
        return time != null ? time.toMillis() : null;
    }

    private static String formatTime(@Nullable Long millis) {
        if (millis == null || millis <= 0) {
            return "-";
        }
        return DateTimeFormatters.format(millis);
    }

    private static String orDash(String value) {
        return value.isEmpty() ? "-" : value;
    }
}
