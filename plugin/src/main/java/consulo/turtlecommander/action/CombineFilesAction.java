package consulo.turtlecommander.action;

import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.ui.FileTab;
import consulo.turtlecommander.ui.FileTabOperationUtil;
import consulo.util.lang.StringUtil;

/** Reassembles a split file. Offered only on a chunk or its .crc sidecar. */
@ActionImpl(id = "TurtleCommander.CombineFiles")
public class CombineFilesAction extends FileContextAction.Simple {
    public CombineFilesAction() {
        super(LocalizeValue.localizeTODO("Combine Files..."), null,
            CombineFilesAction::isSplitChunkOrCrc, true, FileTabOperationUtil::performCombineFiles);
    }

    /** A chunk is named {@code name.001}; the checksum sidecar is {@code name.crc}. */
    private static boolean isSplitChunkOrCrc(FileTab tab, FileEntry entry) {
        if (!FileContextAction.isFileOutsideArchive(tab, entry)) {
            return false;
        }
        String extension = StringUtil.substringAfterLast(entry.name(), ".");
        if (extension.isEmpty()) {
            return false;
        }
        return "crc".equals(extension)
            || (extension.length() >= 3 && extension.length() <= 6 && StringUtil.isNotNegativeNumber(extension));
    }
}
