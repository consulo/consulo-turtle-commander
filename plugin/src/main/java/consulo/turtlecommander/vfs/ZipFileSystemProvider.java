package consulo.turtlecommander.vfs;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public class ZipFileSystemProvider implements VirtualFileSystemProvider {
    public static final Set<String> ARCHIVE_EXTENSIONS = Set.of(
        // Plain ZIP and Java/Android variants
        "zip", "jar", "war", "ear", "apk", "aar", "aab", "apkg",
        // iOS app packages (plain ZIPs with a Payload/ directory)
        "ipa",
        // Windows app packages (OPC ZIPs; bundles nest the per-arch packages)
        "appx", "appxbundle", "msix", "msixbundle",
        // Microsoft Office Open XML (incl. macro-enabled / template / Visio variants)
        "docx", "docm", "dotx", "dotm",
        "xlsx", "xlsm", "xltx", "xltm", "xlsb", "xlam",
        "pptx", "pptm", "potx", "potm", "ppsx", "ppsm", "ppam",
        "vsdx", "vsdm", "vstx", "vstm", "thmx",
        // XPS / OpenXPS documents (OPC ZIPs)
        "xps", "oxps",
        // OpenDocument (documents + templates)
        "odt", "ods", "odp", "odg", "odf", "odb", "odc", "odm",
        "ott", "ots", "otp", "otg",
        // eBooks / comics
        "epub", "cbz",
        // NuGet
        "nupkg",
        // Firefox / Mozilla browser extensions (plain ZIPs)
        "xpi",
        // VS Code / Visual Studio extensions (plain ZIPs)
        "vsix",
        // Quake 3 / Doom 3 (id Tech 3/4) game data (plain ZIPs)
        "pk3", "pk4",
        // Python wheels (PEP 427) and legacy setuptools eggs
        "whl", "egg",
        // Google Earth KML (zipped KML + assets)
        "kmz",
        // Universal Scene Description (Pixar USD / Apple AR Quick Look, uncompressed ZIP)
        "usdz",
        // LibreOffice / OpenOffice extensions (plain ZIPs)
        "oxt"
    );

    @Override
    public boolean supportsExtension(String extension) {
        return ARCHIVE_EXTENSIONS.contains(extension);
    }

    @Override
    public VirtualFileSystem create(Path archivePath) throws IOException {
        return create(archivePath, null);
    }

    @Override
    public VirtualFileSystem create(Path archivePath, @Nullable VfsOpenProgress openProgress) throws IOException {
        try {
            return new ZipVirtualFileSystem(archivePath);
        }
        catch (Exception e) {
            // Java's ZipFileSystem rejects some valid ZIPs (invalid CEN header, etc.); fall back to
            // Commons Compress extraction.
            return new ZipExtractVirtualFileSystem(archivePath, openProgress);
        }
    }
}
