package consulo.turtlecommander.ui;

import consulo.logging.Logger;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.util.FileUtils;
import consulo.turtlecommander.util.UiTask;
import consulo.turtlecommander.vfs.VirtualFileSystem;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Drag out of a panel, and drop onto one. */
public class FileEntryTransferHandler extends TransferHandler {
    private static final Logger LOG = Logger.getInstance(FileEntryTransferHandler.class);

    private final FileTab myTab;

    public FileEntryTransferHandler(FileTab tab) {
        myTab = tab;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return COPY;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        List<FileEntry> entries = FileTabSelectionUtil.getSelectedEntries(myTab);
        return entries.isEmpty() ? null : new FileEntryTransferable(entries, myTab.getProject());
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (!support.isDrop() || isReadOnlyArchive()) {
            return false;
        }
        return support.isDataFlavorSupported(FileEntryTransferable.FILE_ENTRY_FLAVOR)
            || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!support.isDrop()) {
            return false;
        }
        if (isReadOnlyArchive()) {
            myTab.fileErrorNotification("Cannot copy into a read-only archive");
            return false;
        }
        try {
            if (support.isDataFlavorSupported(FileEntryTransferable.FILE_ENTRY_FLAVOR)) {
                return importFileEntries(support);
            }
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                return importExternalFiles(support);
            }
            return false;
        }
        catch (Exception e) {
            LOG.warn("Drop failed: " + FileUtils.fileErrorMessage(e));
            return false;
        }
    }

    private boolean isReadOnlyArchive() {
        VirtualFileSystem vfs = myTab.getCurrentVfs();
        return vfs != null && vfs.isReadOnly();
    }

    @SuppressWarnings("unchecked")
    private boolean importFileEntries(TransferSupport support) throws Exception {
        List<FileEntry> entries = (List<FileEntry>) support.getTransferable()
            .getTransferData(FileEntryTransferable.FILE_ENTRY_FLAVOR);
        FileTabOperationUtil.performCopyEntries(myTab, entries, myTab.getCurrentPath(),
            FileTabVfsUtil.getDisplayPath(myTab));
        return true;
    }

    /**
     * A drop from outside the IDE. The transferable has to be read here, while the drop is still
     * live, but the per-file stats can block on a network share — so only the read happens inline
     * and the entries are built off the UI thread.
     */
    @SuppressWarnings("unchecked")
    private boolean importExternalFiles(TransferSupport support) throws Exception {
        List<File> files = (List<File>) support.getTransferable()
            .getTransferData(DataFlavor.javaFileListFlavor);
        Path destination = myTab.getCurrentPath();
        String displayPath = FileTabVfsUtil.getDisplayPath(myTab);

        UiTask.background(() -> {
            List<FileEntry> entries = new ArrayList<>(files.size());
            for (File file : files) {
                entries.add(FileEntry.builder(file.getName(), file.toPath())
                    .directory(file.isDirectory())
                    .size(file.isFile() ? file.length() : 0)
                    .build());
            }
            UiTask.ui(() -> FileTabOperationUtil.performCopyEntries(myTab, entries, destination, displayPath));
        });
        return true;
    }
}
