package consulo.turtlecommander.vfs;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsupportedRarEncryptedException;
import com.github.junrar.exception.UnsupportedRarVersionException;
import com.github.junrar.rarfile.FileHeader;
import consulo.application.Application;
import consulo.ui.ex.awt.Messages;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * RAR archives via junrar, falling back to a system 7-Zip binary for format revisions junrar can't
 * read and for encrypted archives it refuses. Read-only: junrar cannot write RAR.
 */
public class RarVirtualFileSystem extends AbstractTempDirVirtualFileSystem {
    private static final byte[] RAR4_SIGNATURE = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00};
    private static final byte[] RAR5_SIGNATURE = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00};

    /** Signals that junrar needs a password before it can read anything. */
    private static final class PasswordRequired extends RuntimeException {
        PasswordRequired() {
            super(null, null, false, false);
        }
    }

    public RarVirtualFileSystem(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        super(archivePath, "turtle-rar-", openProgress);
        openTempDir();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    protected void extract(Path into) throws IOException {
        VfsOpenProgress progress = takeOpenProgress();
        if (!sniffIsRar()) {
            throw new SilentVfsOpenException();
        }

        try {
            junrarExtract(into, null, progress);
            return;
        }
        catch (PasswordRequired e) {
            // Fall through to the password prompt below.
        }
        catch (UnsupportedRarVersionException e) {
            reset(into);
            sevenZipFallback(into, progress, null,
                "This RAR archive uses a format revision that can't be read in-process. Install "
                    + "7-Zip / p7zip and ensure 7z, 7zz, or 7za is on PATH (or in the standard 7-Zip "
                    + "install location on Windows) to open it.");
            return;
        }
        catch (RarException e) {
            if (!isPasswordIssue(e)) {
                throw new IOException("Failed to read RAR archive: " + e.getMessage(), e);
            }
        }

        String password = promptPassword();
        if (password == null) {
            throw new IOException("Password required to open " + getArchivePath().getFileName() + ".");
        }

        try {
            reset(into);
            junrarExtract(into, password, progress);
        }
        catch (Exception e) {
            reset(into);
            sevenZipFallback(into, progress, password,
                "This encrypted RAR couldn't be read in-process. Install 7-Zip / p7zip (7z, 7zz, or "
                    + "7za on PATH, or the standard 7-Zip install on Windows) to open it.");
        }
    }

    private void junrarExtract(Path into, @Nullable String password, VfsOpenProgress progress)
        throws IOException, RarException {

        try (Archive archive = password == null
            ? new Archive(getArchivePath().toFile())
            : new Archive(getArchivePath().toFile(), password)) {

            if (password == null && archive.isPasswordProtected()) {
                throw new PasswordRequired();
            }

            for (FileHeader header : archive.getFileHeaders()) {
                if (progress.isCancelled()) {
                    break;
                }
                if (password == null && header.isEncrypted()) {
                    throw new PasswordRequired();
                }

                String rawName = header.getFileName();
                if (rawName == null) {
                    continue;
                }
                progress.onEntry(0, 0, rawName);

                Path target = VfsPaths.resolveEntryPath(into, rawName.replace('\\', '/'));
                if (target == null) {
                    continue;
                }

                if (header.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }

                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (OutputStream out = Files.newOutputStream(target)) {
                    archive.extractFile(header, out);
                }
                try {
                    if (header.getLastModifiedTime() != null) {
                        Files.setLastModifiedTime(target, header.getLastModifiedTime());
                    }
                }
                catch (Exception e) {
                    // A timestamp we can't apply is not worth failing the extraction over.
                }
            }
        }
    }

    private void sevenZipFallback(Path into, VfsOpenProgress progress, @Nullable String password,
                                  String missingBinaryMessage) throws IOException {
        if (System7z.findBinary() == null) {
            throw new IOException(missingBinaryMessage);
        }

        try {
            System7z.extract(getArchivePath(), into, progress, password);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("RAR extraction was cancelled", e);
        }
        catch (IOException e) {
            if (password != null || !looksEncrypted(e)) {
                throw e;
            }
            String prompted = promptPassword();
            if (prompted == null) {
                throw new IOException("Password required to open " + getArchivePath().getFileName() + ".");
            }
            reset(into);
            try {
                System7z.extract(getArchivePath(), into, progress, prompted);
            }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("RAR extraction was cancelled", interrupted);
            }
        }
    }

    private boolean sniffIsRar() {
        byte[] header = new byte[8];
        int read;
        try (InputStream stream = Files.newInputStream(getArchivePath())) {
            read = stream.readNBytes(header, 0, header.length);
        }
        catch (Exception e) {
            return false;
        }
        return (read >= RAR5_SIGNATURE.length && startsWith(header, RAR5_SIGNATURE))
            || (read >= RAR4_SIGNATURE.length && startsWith(header, RAR4_SIGNATURE));
    }

    private @Nullable String promptPassword() {
        String[] result = new String[1];
        Application.get().invokeAndWait(() -> result[0] = Messages.showPasswordDialog(
            "Enter password for " + getArchivePath().getFileName() + ":",
            "Encrypted RAR Archive"));

        return result[0] != null && !result[0].isEmpty() ? result[0] : null;
    }

    private static boolean startsWith(byte[] value, byte[] signature) {
        return Arrays.equals(value, 0, signature.length, signature, 0, signature.length);
    }

    private static boolean isPasswordIssue(Throwable e) {
        for (Throwable current = e; current != null; current = current.getCause()) {
            if (current instanceof UnsupportedRarEncryptedException) {
                return true;
            }
            if (mentionsEncryption(current.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksEncrypted(Throwable e) {
        for (Throwable current = e; current != null; current = current.getCause()) {
            if (mentionsEncryption(current.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsEncryption(@Nullable String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("password") || lower.contains("encrypt");
    }

    private static void reset(Path dir) throws IOException {
        VfsPaths.deleteRecursively(dir);
        Files.createDirectories(dir);
    }
}
