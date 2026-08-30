package consulo.turtlecommander.dialog;

import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.TextFieldContextMenuUtil;
import consulo.turtlecommander.util.DateTimeFormatters;
import consulo.turtlecommander.util.FileUtils;
import consulo.turtlecommander.util.UiTask;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.FormBuilder;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBTextField;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import org.jspecify.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cross-platform fallback Properties dialog. Shown when no native dialog is available — unknown
 * Linux DE, or any host where the path isn't a real filesystem entry (the inside-archive case).
 * Read-only by design.
 *
 * <p>All values are rendered as transparent, non-editable {@link JBTextField}s so the user can
 * select text and copy it. Visually they read as labels — no border, no filled background.
 *
 * <p>For directories the recursive size + file/folder count is computed on a pooled thread; the
 * field is updated when the walk finishes (or stays at "Calculating…" if the dialog is closed
 * first — the cancelled flag short-circuits the visitor).
 */
public class PropertiesDialog extends DialogWrapper {
    private final FileEntry myEntry;
    private final JBTextField mySizeField;
    private final JBTextField myAccessedField = copyableField("\u2026");
    private final AtomicBoolean myCancelled = new AtomicBoolean(false);

    public PropertiesDialog(@Nullable Project project, FileEntry entry) {
        super(project);
        myEntry = entry;
        mySizeField = copyableField(initialSizeText());

        setTitle("Properties");
        init();

        if (entry.directory()) {
            UiTask.background(this::computeAndShowDirSize);
        }
        // The access-time stat is one syscall, but on a dead network path it can hang for the SMB
        // timeout — read it off the EDT like the directory size.
        UiTask.backgroundThenUi(() -> readAccessTime(entry.path()), millis -> {
            if (!myCancelled.get()) {
                myAccessedField.setText(formatTime(millis));
            }
        });
    }

    @Override
    protected void dispose() {
        myCancelled.set(true);
        super.dispose();
    }

    /** Single Close button — this dialog is read-only. */
    @Override
    protected Action[] createActions() {
        Action ok = getOKAction();
        ok.putValue(Action.NAME, "Close");
        return new Action[]{ok};
    }

    @Override
    protected JComponent createCenterPanel() {
        FormBuilder builder = FormBuilder.createFormBuilder()
            .addLabeledComponent("Type:", copyableField(typeDescription()))
            .addLabeledComponent("Path:", copyableField(myEntry.path().toAbsolutePath().toString()))
            .addLabeledComponent("Location:", copyableField(locationText()))
            .addLabeledComponent("Size:", mySizeField)
            .addLabeledComponent("Created:", copyableField(formatTime(toMillis(myEntry.creationTime()))))
            .addLabeledComponent("Modified:", copyableField(formatTime(toMillis(myEntry.lastModified()))))
            .addLabeledComponent("Accessed:", myAccessedField)
            .addLabeledComponent("Owner:", copyableField(orDash(myEntry.owner())))
            .addLabeledComponent("Group:", copyableField(orDash(myEntry.group())))
            .addLabeledComponent("Permissions:", copyableField(orDash(myEntry.permissions())));

        JPanel form = new JPanel(new BorderLayout());
        form.setBorder(JBUI.Borders.empty(8));
        form.add(builder.getPanel(), BorderLayout.NORTH);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setPreferredSize(new Dimension(520, 320));
        root.setBorder(JBUI.Borders.empty(8, 4, 0, 4));
        root.add(headerPanel(), BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        return root;
    }

    private JComponent headerPanel() {
        Image icon = myEntry.directory() ? PlatformIconGroup.nodesFolder() : fileTypeIcon();
        JBLabel iconLabel = new JBLabel(icon);
        iconLabel.setBorder(JBUI.Borders.emptyRight(8));

        // Filename gets the same copy-friendly treatment, just bold/larger.
        JBTextField nameField = copyableField(myEntry.name());
        Font font = nameField.getFont();
        nameField.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 2f));

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(JBUI.Borders.empty(0, 4, 8, 0));
        header.add(iconLabel, BorderLayout.WEST);
        header.add(nameField, BorderLayout.CENTER);
        return header;
    }

    private Image fileTypeIcon() {
        Image icon = FileTypeRegistry.getInstance().getFileTypeByFileName(myEntry.name()).getIcon();
        return icon != null ? icon : PlatformIconGroup.filetypesAny_type();
    }

    /**
     * A label-shaped, non-editable, copyable text field. No border, no fill — visually reads as a
     * {@link JBLabel} but the text can be selected and copied.
     */
    private static JBTextField copyableField(String text) {
        JBTextField field = new JBTextField(text);
        field.setEditable(false);
        field.setOpaque(false);
        field.setBorder(JBUI.Borders.empty());
        // The blinking caret on a non-editable field is distracting; hide it.
        field.getCaret().setVisible(false);
        TextFieldContextMenuUtil.installStandardContextMenu(field);
        return field;
    }

    private String typeDescription() {
        if (myEntry.directory()) {
            return "Directory";
        }
        if (myEntry.namedPipe()) {
            return "Named pipe (FIFO)";
        }
        FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(myEntry.name());
        // The unknown type's description is just "Unknown" — not very informative. Synthesize a
        // name from the extension instead.
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
            return "Calculating\u2026";
        }
        return FileUtils.formatSize(myEntry.size()) + " (" + String.format("%,d", myEntry.size()) + " bytes)";
    }

    private void computeAndShowDirSize() {
        DirSizeVisitor visitor = new DirSizeVisitor();
        try {
            Files.walkFileTree(myEntry.path(), visitor);
        }
        catch (Exception e) {
            // Permission denied on the root, or the path no longer exists — show what we have.
        }
        if (myCancelled.get()) {
            return;
        }
        String text = FileUtils.formatSize(visitor.myBytes) + " (" + String.format("%,d", visitor.myBytes)
            + " bytes), " + visitor.myFiles + " files, " + visitor.myDirs + " folders";
        UiTask.ui(() -> {
            if (!myCancelled.get()) {
                mySizeField.setText(text);
            }
        });
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
