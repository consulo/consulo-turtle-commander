package consulo.turtlecommander.util;

import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.ShellAPI;
import consulo.application.util.SystemInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Native "Properties / Get Info" dialog bridge.
 *
 * <ul>
 * <li><b>Windows</b>: JNA {@code Shell32.ShellExecuteEx} with the {@code "properties"} verb.
 * {@code SEE_MASK_INVOKEIDLIST} is required: the verb is implemented as an IDLIST command, not
 * a registered shell-execute verb, so without the flag Windows silently does nothing.</li>
 * <li><b>macOS</b>: AppleScript {@code open information window of (POSIX file …)} via
 * {@code osascript}. The Get Info window is owned by Finder, which we {@code activate} so it
 * comes to the front instead of opening behind the IDE.</li>
 * <li><b>Linux</b>: dispatch by {@code XDG_CURRENT_DESKTOP}. KDE → {@code kioclient6 properties}
 * (with {@code kioclient5} fallback). GNOME-like → {@code gdbus} against
 * {@code org.freedesktop.FileManager1.ShowItemProperties}. Anything else returns false; the
 * caller falls back to the built-in dialog.</li>
 * </ul>
 */
public final class NativeProperties {
    private static final int SEE_MASK_INVOKEIDLIST = 0x0000000C;
    private static final int SW_SHOW = 5;

    private static final Set<String> GNOME_LIKE = Set.of(
        "GNOME", "UNITY", "CINNAMON", "X-CINNAMON", "MATE", "PANTHEON", "BUDGIE", "GNOME-CLASSIC"
    );

    private NativeProperties() {
    }

    /**
     * Opens the platform Properties / Get Info dialog for {@code path}. The dialog runs on its
     * owner-process thread (Shell on Windows, Finder on macOS, Nautilus / Dolphin on Linux), so
     * this returns immediately. Returns false if the platform has no native dialog or the call
     * fails; never throws.
     */
    public static boolean showProperties(Path path) {
        if (SystemInfo.isWindows) {
            return showOnWindows(path);
        }
        if (SystemInfo.isMac) {
            return showOnMac(path);
        }
        if (SystemInfo.isUnix) {
            return showOnLinux(path);
        }
        return false;
    }

    private static boolean showOnWindows(Path path) {
        try {
            ShellAPI.SHELLEXECUTEINFO info = new ShellAPI.SHELLEXECUTEINFO();
            info.cbSize = info.size();
            info.fMask = SEE_MASK_INVOKEIDLIST;
            info.lpVerb = "properties";
            info.lpFile = path.toAbsolutePath().toString();
            info.nShow = SW_SHOW;
            return Shell32.INSTANCE.ShellExecuteEx(info);
        }
        catch (Throwable e) {
            // UnsatisfiedLinkError on a non-Windows JVM, or any other native failure
            return false;
        }
    }

    private static boolean showOnMac(Path path) {
        // The path is passed as an argv parameter rather than spliced into the script body:
        // filenames legally contain double-quotes on macOS. `--` ends osascript options so a
        // filename starting with `-` isn't mistaken for a flag.
        //
        // The `as alias` coercion is load-bearing: `POSIX file "..."` returns a furl that Finder
        // won't accept as a Finder item, so `open information window of <furl>` silently degrades
        // to a plain `open`. Coercing to an alias inside the `tell` block keeps it in Finder's
        // scope; computing it outside lets Finder re-coerce it back to a furl.
        //
        // `activate` comes after `open information window` so a scripting error aborts before
        // Finder is brought forward — otherwise Finder pops up with no info window.
        try {
            Process process = new ProcessBuilder(
                "osascript",
                "-e", "on run argv",
                "-e", "tell application \"Finder\"",
                "-e", "open information window of (POSIX file (item 1 of argv) as alias)",
                "-e", "activate",
                "-e", "end tell",
                "-e", "end run",
                "--",
                path.toAbsolutePath().toString()
            ).redirectErrorStream(true).start();

            // A successful Get Info completes well under a second. A non-zero exit within the
            // budget is a real failure, so the caller can fall back. If it hasn't exited yet,
            // assume Finder is still drawing and treat as success rather than blocking longer.
            if (process.waitFor(1, TimeUnit.SECONDS)) {
                return process.exitValue() == 0;
            }
            return true;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        catch (Throwable e) {
            return false;
        }
    }

    private static boolean showOnLinux(Path path) {
        String xdg = System.getenv("XDG_CURRENT_DESKTOP");
        String desktop = xdg != null ? xdg.toUpperCase(java.util.Locale.ROOT) : "";

        if (desktop.contains("KDE")) {
            return tryKde(path);
        }
        for (String token : desktop.split(":")) {
            if (GNOME_LIKE.contains(token)) {
                return tryGnome(path);
            }
        }
        return false;
    }

    private static boolean tryKde(Path path) {
        // KDE 6 ships kioclient6, KDE 5 ships kioclient5. ProcessBuilder.start() throws
        // synchronously when the binary isn't on PATH, so "missing" is detectable without waiting.
        String uri = path.toUri().toString();
        for (String command : List.of("kioclient6", "kioclient5")) {
            try {
                new ProcessBuilder(command, "properties", uri).start();
                return true;
            }
            catch (Throwable e) {
                // try the next variant
            }
        }
        return false;
    }

    private static boolean tryGnome(Path path) {
        // org.freedesktop.FileManager1 is the standardised D-Bus interface implemented by
        // Nautilus, Nemo and Caja. ShowItemProperties takes an array of URIs and a startup id.
        String uri = path.toUri().toString();
        try {
            new ProcessBuilder(
                "gdbus", "call", "--session",
                "--dest", "org.freedesktop.FileManager1",
                "--object-path", "/org/freedesktop/FileManager1",
                "--method", "org.freedesktop.FileManager1.ShowItemProperties",
                "['" + uri + "']", ""
            ).start();
            return true;
        }
        catch (Throwable e) {
            return false;
        }
    }
}
