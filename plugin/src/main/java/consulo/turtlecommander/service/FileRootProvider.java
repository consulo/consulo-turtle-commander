package consulo.turtlecommander.service;

import consulo.application.Application;
import consulo.fileChooser.PlatformFileChooserContributorProvider;
import consulo.fileChooser.UniversalFileChooserContributor;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.platform.PlatformFileSystem;
import consulo.platform.PlatformOperatingSystem;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.turtlecommander.model.FileRoot;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;

import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Collects the roots the path bar offers: this machine's, then those of every remote platform the file chooser
 * lists through its {@link UniversalFileChooserContributor contributors}.
 *
 * <p>This machine is not read from its contributor, which reports the file system roots only - "/" on Unix - and
 * would leave removable media and network shares unreachable; its home folder and mount points are added here.
 * A remote platform is only asked once it is connected: asking a target that is not would connect to it, and the
 * roots are polled.
 */
public final class FileRootProvider {
    private static final Logger LOG = Logger.getInstance(FileRootProvider.class);

    /** How long one remote platform may take to answer before the poll goes on without its roots. */
    private static final long REMOTE_ROOTS_TIMEOUT_SECONDS = 5;

    private final Application myApplication;
    private final Platform myPlatform;

    public FileRootProvider(Application application, Platform platform) {
        myApplication = application;
        myPlatform = platform;
    }

    /**
     * This machine's roots, then the connected remote platforms'. Blocks: enumerating drives, reading volume labels
     * and asking a remote platform can each stall for seconds, so call it off the UI thread.
     */
    public List<FileRoot> collectRoots() {
        Map<Path, FileRoot> roots = new LinkedHashMap<>();
        collectLocalRoots(roots);
        for (UniversalFileChooserContributor contributor : contributors()) {
            Platform platform = contributor.getPlatform();
            if (platform != null && platform != myPlatform) {
                collectRemoteRoots(roots, contributor);
            }
        }
        return List.copyOf(roots.values());
    }

    /** The chooser's own contributors, then the remote targets the providers add as tabs of their own. */
    private List<UniversalFileChooserContributor> contributors() {
        List<UniversalFileChooserContributor> contributors =
            new ArrayList<>(myApplication.getExtensionPoint(UniversalFileChooserContributor.class).getExtensionList());
        myApplication.getExtensionPoint(PlatformFileChooserContributorProvider.class)
            .forEach(provider -> contributors.addAll(provider.getContributors()));
        return contributors;
    }

    private static void collectRemoteRoots(Map<Path, FileRoot> roots, UniversalFileChooserContributor contributor) {
        List<UniversalFileChooserContributor.Root> contributed;
        try {
            contributed = contributor.getRoots().get(REMOTE_ROOTS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        catch (Exception e) {
            LOG.debug("Cannot list the roots of " + contributor.getTabTitle().get(), e);
            return;
        }

        String location = contributor.getTabTitle().get();
        for (UniversalFileChooserContributor.Root root : contributed) {
            Path path = root.path();
            // a virtual root has no path until it is mounted
            if (path == null) {
                continue;
            }
            UniversalFileChooserContributor.Presentation presentation = root.presentation();
            Image icon = presentation.icon();
            roots.putIfAbsent(path, new FileRoot(path, presentation.presentableName().get(),
                icon != null ? icon : PlatformIconGroup.nodesFolder(), location));
        }
    }

    /** The file system roots, the user's home, then mounted volumes. */
    private void collectLocalRoots(Map<Path, FileRoot> roots) {
        PlatformFileSystem fs = myPlatform.fs();
        PlatformOperatingSystem os = myPlatform.os();

        for (Path root : fs.getRootDirectories()) {
            add(roots, root, os.isWindows() ? windowsDriveName(root) : root.toString(), PlatformIconGroup.nodesFolder());
        }

        Path home = myPlatform.user().homePath();
        if (Files.isDirectory(home)) {
            add(roots, home, home.toString(), PlatformIconGroup.nodesHomefolder());
        }

        // On Unix the platform reports only "/", so removable media and network shares would be
        // unreachable from the selector. Walk the usual mount directories until the platform reports
        // mount points itself.
        if (os.isMac()) {
            addMounts(roots, fs.getPath("/Volumes"));
        }
        else if (!os.isWindows()) {
            addMounts(roots, fs.getPath("/media"));
            addMounts(roots, fs.getPath("/mnt"));
            addMounts(roots, fs.getPath("/run/media", myPlatform.user().name()));
        }
    }

    private static void add(Map<Path, FileRoot> roots, Path path, String presentableName, Image icon) {
        roots.putIfAbsent(path, new FileRoot(path, presentableName, icon));
    }

    private static void addMounts(Map<Path, FileRoot> roots, Path mountDir) {
        if (!Files.isDirectory(mountDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mountDir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    add(roots, child, child.toString(), PlatformIconGroup.nodesFolder());
                }
            }
        }
        catch (Exception e) {
            LOG.debug("Cannot list mount point directory " + mountDir, e);
        }
    }

    /** "C:\" with a labelled volume reads as "System (C:)", the way Explorer shows it. */
    private static String windowsDriveName(Path root) {
        String path = root.toString();
        try {
            FileStore store = Files.getFileStore(root);
            String label = store.name();
            if (!StringUtil.isEmptyOrSpaces(label)) {
                return label + " (" + StringUtil.trimEnd(path, root.getFileSystem().getSeparator()) + ")";
            }
        }
        catch (Exception e) {
            // An empty card reader or a dead network drive has no store; show the bare drive.
        }
        return path;
    }
}
