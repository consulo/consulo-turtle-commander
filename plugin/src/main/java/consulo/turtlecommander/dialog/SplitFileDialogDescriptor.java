package consulo.turtlecommander.dialog;

import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.util.FileUtils;
import consulo.ui.Alerts;
import consulo.ui.Component;
import consulo.ui.IntBox;
import consulo.ui.Label;
import consulo.ui.RadioGroup;
import consulo.ui.Space;
import consulo.ui.TextBox;
import consulo.ui.TextBoxWithHistory;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.DialogValue;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.Indenter;
import consulo.ui.util.LabeledBuilder;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Splits one file into fixed-size chunks or into a fixed number of parts. */
public final class SplitFileDialogDescriptor extends DialogDescriptor {
    /** Display label to chunk size in bytes; insertion order drives the drop-down. */
    private static final Map<String, Long> PRESET_SIZES = new LinkedHashMap<>();

    static {
        PRESET_SIZES.put("1 MB", 1L * 1024 * 1024);
        PRESET_SIZES.put("1.44 MB (Floppy)", 1_457_664L);
        PRESET_SIZES.put("5 MB", 5L * 1024 * 1024);
        PRESET_SIZES.put("10 MB", 10L * 1024 * 1024);
        PRESET_SIZES.put("100 MB (Zip)", 100L * 1024 * 1024);
        PRESET_SIZES.put("650 MB (CD)", 650L * 1024 * 1024);
        PRESET_SIZES.put("700 MB (CD)", 700L * 1024 * 1024);
        PRESET_SIZES.put("1 GB", 1L * 1024 * 1024 * 1024);
        PRESET_SIZES.put("2 GB", 2L * 1024 * 1024 * 1024);
        PRESET_SIZES.put("4 GB (FAT32)", 4L * 1024 * 1024 * 1024 - 1);
        PRESET_SIZES.put("4.7 GB (DVD)", (long) (4.7 * 1024 * 1024 * 1024));
        PRESET_SIZES.put("8.5 GB (DVD DL)", (long) (8.5 * 1024 * 1024 * 1024));
        PRESET_SIZES.put("25 GB (Blu-ray)", 25L * 1024 * 1024 * 1024);
    }

    private static final Map<String, Long> SUFFIX_MULTIPLIERS = new LinkedHashMap<>();

    static {
        SUFFIX_MULTIPLIERS.put("TB", 1L * 1024 * 1024 * 1024 * 1024);
        SUFFIX_MULTIPLIERS.put("GB", 1L * 1024 * 1024 * 1024);
        SUFFIX_MULTIPLIERS.put("MB", 1L * 1024 * 1024);
        SUFFIX_MULTIPLIERS.put("KB", 1024L);
    }

    private static final int MIN_PARTS = 2;
    private static final int MAX_PARTS = 9999;

    private enum SplitMode {
        BY_SIZE,
        BY_PARTS
    }

    private final String myFileName;
    private final long myFileSize;
    private final String myInitialTargetDirectory;

    private @Nullable TextBox myTargetDirBox;
    private @Nullable RadioGroup<SplitMode> myModeGroup;
    private @Nullable TextBoxWithHistory mySizeBox;
    private @Nullable IntBox myPartsBox;

    // read back in canHandle, so the getters answer the same after the dialog and its components are gone
    private String myTargetDirectory;
    private boolean mySplitBySize = true;
    private long myChunkSize;
    private int myNumberOfParts = MIN_PARTS;

    public SplitFileDialogDescriptor(String fileName, long fileSize, String targetDirectory) {
        super(LocalizeValue.localizeTODO("Split File"));
        myFileName = fileName;
        myFileSize = fileSize;
        myInitialTargetDirectory = targetDirectory;
        myTargetDirectory = targetDirectory.trim();
        myChunkSize = PRESET_SIZES.values().iterator().next();
    }

    public String getTargetDirectory() {
        return myTargetDirectory;
    }

    public boolean isSplitBySize() {
        return mySplitBySize;
    }

    public long getChunkSize() {
        return myChunkSize;
    }

    public int getNumberOfParts() {
        return myNumberOfParts;
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        VerticalLayout layout = VerticalLayout.create(Space.SMALL);
        layout.add(Label.create(LocalizeValue.of(
            "Split \"" + myFileName + "\" (" + FileUtils.formatSize(myFileSize) + ")")));

        layout.add(Label.create(LocalizeValue.localizeTODO("Target directory:")));
        TextBox targetDirBox = TextBox.create(myInitialTargetDirectory);
        myTargetDirBox = targetDirBox;
        // a dock center stretches the box over the width of the dialog; added as is, it kept its default width
        layout.add(DockLayout.create().center(targetDirBox));

        // the presets drop down, but any size like "3.5 MB" or a plain byte count can be typed
        List<String> presets = new ArrayList<>(PRESET_SIZES.keySet());
        TextBoxWithHistory sizeBox = TextBoxWithHistory.create(presets.get(0)).setHistory(presets);
        mySizeBox = sizeBox;

        IntBox partsBox = IntBox.create(MIN_PARTS).withRange(MIN_PARTS, MAX_PARTS);
        myPartsBox = partsBox;

        RadioGroup<SplitMode> modeGroup = RadioGroup.create();
        myModeGroup = modeGroup;
        layout.add(modeGroup.newButton(LocalizeValue.localizeTODO("Split by size"), SplitMode.BY_SIZE));
        layout.add(Indenter.indent(LabeledBuilder.filled(LocalizeValue.localizeTODO("Chunk size:"), sizeBox)));
        layout.add(modeGroup.newButton(LocalizeValue.localizeTODO("Split by number of parts"), SplitMode.BY_PARTS));
        layout.add(Indenter.indent(LabeledBuilder.filled(LocalizeValue.localizeTODO("Number of parts:"), partsBox)));

        modeGroup.addValueListener(this::updateEnabled);
        modeGroup.setValue(SplitMode.BY_SIZE, false);
        updateEnabled(SplitMode.BY_SIZE);

        return layout;
    }

    /** Only the field of the chosen mode is editable, so it is clear which one the split uses. */
    @RequiredUIAccess
    private void updateEnabled(@Nullable SplitMode mode) {
        boolean bySize = mode != SplitMode.BY_PARTS;
        TextBoxWithHistory sizeBox = mySizeBox;
        if (sizeBox != null) {
            sizeBox.setEnabled(bySize);
        }
        IntBox partsBox = myPartsBox;
        if (partsBox != null) {
            partsBox.setEnabled(!bySize);
        }
    }

    @Override
    public Component getPreferredFocusedComponent() {
        TextBox targetDirBox = myTargetDirBox;
        return targetDirBox != null ? targetDirBox : super.getPreferredFocusedComponent();
    }

    @Override
    protected DialogOkAction createOkAction() {
        return new DialogOkAction(LocalizeValue.localizeTODO("Split"));
    }

    /** A blank directory, an unreadable chunk size or a part count out of range keeps the dialog open, with the reason. */
    @RequiredUIAccess
    @Override
    public boolean canHandle(AnAction action, @Nullable DialogValue value) {
        if (!(action instanceof DialogOkAction)) {
            return true;
        }
        TextBox targetDirBox = myTargetDirBox;
        String targetDirectory = targetDirBox != null ? targetDirBox.getValueOrError().trim() : myTargetDirectory;
        if (targetDirectory.isEmpty()) {
            Alerts.okError(LocalizeValue.localizeTODO("Target directory cannot be empty.")).showAsync();
            return false;
        }

        RadioGroup<SplitMode> modeGroup = myModeGroup;
        boolean splitBySize = modeGroup == null || modeGroup.getValue() != SplitMode.BY_PARTS;

        long chunkSize = myChunkSize;
        int numberOfParts = myNumberOfParts;
        if (splitBySize) {
            TextBoxWithHistory sizeBox = mySizeBox;
            if (sizeBox != null) {
                chunkSize = toChunkSize(sizeBox.getValueOrError());
            }
            if (chunkSize <= 0) {
                Alerts.okError(LocalizeValue.localizeTODO("Invalid chunk size.")).showAsync();
                return false;
            }
        }
        else {
            IntBox partsBox = myPartsBox;
            // the range bounds the spinner, but a typed value may still be outside it or not a number at all
            Integer parts = partsBox != null ? partsBox.getValue() : Integer.valueOf(numberOfParts);
            if (parts == null || parts < MIN_PARTS || parts > MAX_PARTS) {
                Alerts.okError(LocalizeValue.of("Number of parts must be between " + MIN_PARTS + " and " + MAX_PARTS + "."))
                    .showAsync();
                return false;
            }
            numberOfParts = parts;
        }

        myTargetDirectory = targetDirectory;
        mySplitBySize = splitBySize;
        myChunkSize = chunkSize;
        myNumberOfParts = numberOfParts;
        return true;
    }

    /** A preset label as listed, or a number with an optional KB/MB/GB/TB suffix; 0 when it is neither. */
    static long toChunkSize(String text) {
        String trimmed = text.trim();
        Long preset = PRESET_SIZES.get(trimmed);
        return preset != null ? preset : parseSize(trimmed);
    }

    private static long parseSize(String text) {
        String trimmed = StringUtil.toUpperCase(text.trim());
        for (Map.Entry<String, Long> entry : SUFFIX_MULTIPLIERS.entrySet()) {
            if (trimmed.endsWith(entry.getKey())) {
                String number = StringUtil.trimEnd(trimmed, entry.getKey()).trim();
                try {
                    return (long) (Double.parseDouble(number) * entry.getValue());
                }
                catch (NumberFormatException e) {
                    return 0L;
                }
            }
        }
        try {
            return Long.parseLong(trimmed);
        }
        catch (NumberFormatException e) {
            return 0L;
        }
    }
}
