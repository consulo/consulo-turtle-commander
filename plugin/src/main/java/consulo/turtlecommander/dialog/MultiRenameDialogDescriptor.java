package consulo.turtlecommander.dialog;

import consulo.application.util.SystemInfo;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.model.FileEntry;
import consulo.turtlecommander.operation.MultiRenameTemplate;
import consulo.turtlecommander.util.UiTask;
import consulo.ui.Alerts;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.HtmlLabel;
import consulo.ui.IntBox;
import consulo.ui.Label;
import consulo.ui.Space;
import consulo.ui.StaticPosition;
import consulo.ui.Table;
import consulo.ui.TextAttribute;
import consulo.ui.TextBox;
import consulo.ui.WidthAndHeight;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.DialogValue;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.TableLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.ui.style.ComponentColors;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Batch rename with a live preview: the template is applied on every keystroke. */
public final class MultiRenameDialogDescriptor extends DialogDescriptor {
    /** Only the entries whose name actually changes. */
    public record Rename(FileEntry entry, String newName) {
    }

    public enum CaseOption {
        UNCHANGED(MultiRenameTemplate.CaseMode.UNCHANGED, "Unchanged"),
        UPPER(MultiRenameTemplate.CaseMode.UPPER, "UPPER"),
        LOWER(MultiRenameTemplate.CaseMode.LOWER, "lower"),
        FIRST_UPPER(MultiRenameTemplate.CaseMode.FIRST_UPPER, "First capital"),
        EVERY_WORD_UPPER(MultiRenameTemplate.CaseMode.EVERY_WORD_UPPER, "Every Word Cap");

        private final MultiRenameTemplate.CaseMode myMode;
        private final String myLabel;

        CaseOption(MultiRenameTemplate.CaseMode mode, String label) {
            myMode = mode;
            myLabel = label;
        }

        public MultiRenameTemplate.CaseMode mode() {
            return myMode;
        }

        @Override
        public String toString() {
            return myLabel;
        }
    }

    /** One line of the preview; the conflict flag travels with the row so the table can colour it. */
    private record PreviewRow(FileEntry entry, String newName, boolean conflict) {
    }

    private static final String HINT = "<html><small>"
        + "[N]=name &nbsp; [N1-5]=chars 1..5 &nbsp; [N3-]=from 3 to end &nbsp; [N2,3]=3 chars from pos 2 &nbsp; "
        + "[N-3]=3rd from end &nbsp; [E]=extension &nbsp; [C]=counter &nbsp; [Y][M][D][h][n][s]=date/time &nbsp; "
        + "[P]=parent dir &nbsp; [G]=grandparent dir &nbsp; "
        + "[R5]=5 random digits &nbsp; [Ra5]/[RA5]/[RM5]=lower/upper/mixed letters &nbsp; "
        + "Regex replace supports $1, $2, …"
        + "</small></html>";

    private final List<FileEntry> myEntries;

    private final MutableFlatDataModel<PreviewRow> myPreviewModel = FlatDataModel.of(List.of());

    private @Nullable TextBox myNameBox;
    private @Nullable TextBox myExtBox;
    private @Nullable TextBox mySearchBox;
    private @Nullable TextBox myReplaceBox;
    private @Nullable CheckBox myRegexCheck;
    private @Nullable CheckBox myCaseSensitiveCheck;
    private @Nullable CheckBox myUseCurrentDateCheck;
    private @Nullable ComboBox<CaseOption> myCaseBox;
    private @Nullable IntBox myCounterStartBox;
    private @Nullable IntBox myCounterStepBox;
    private @Nullable IntBox myCounterWidthBox;

    private Set<Integer> myConflictRows = Set.of();
    private List<String> myCurrentTargets = List.of();
    private List<Rename> myResult = List.of();

    /**
     * Names already present in the entries' parent directories, case-folded on Windows.
     * Snapshotted once on a pooled thread: the conflict preview recomputes on every keystroke, and
     * probing {@code Files.exists} per row per keystroke on the UI thread lags badly on network shares.
     * Until the snapshot lands, targets are optimistically treated as free - the rename operation
     * itself still fails (and rolls back) on a real clash.
     */
    private volatile @Nullable Map<Path, Set<String>> myExistingNames;

    public MultiRenameDialogDescriptor(List<FileEntry> entries) {
        super(LocalizeValue.localizeTODO("Multi-Rename"));
        myEntries = entries;
    }

    /** Populated when the dialog closes with OK. */
    public List<Rename> getResult() {
        return myResult;
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        TextBox nameBox = TextBox.create("[N]");
        TextBox extBox = TextBox.create("[E]");
        TextBox searchBox = TextBox.create("");
        TextBox replaceBox = TextBox.create("");
        CheckBox regexCheck = CheckBox.create(LocalizeValue.localizeTODO("Regex"), false);
        CheckBox caseSensitiveCheck = CheckBox.create(LocalizeValue.localizeTODO("Case sensitive"), true);
        CheckBox useCurrentDateCheck =
            CheckBox.create(LocalizeValue.localizeTODO("Use current date for [Y][M][D][h][n][s]"), false);
        ComboBox<CaseOption> caseBox = ComboBox.create(CaseOption.values());
        caseBox.setTextRenderer(option -> LocalizeValue.of(option == null ? "" : option.toString()));
        caseBox.setValue(CaseOption.UNCHANGED);
        IntBox counterStartBox = IntBox.create(1).withRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
        IntBox counterStepBox = IntBox.create(1).withRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
        IntBox counterWidthBox = IntBox.create(1).withRange(1, 20);

        myNameBox = nameBox;
        myExtBox = extBox;
        mySearchBox = searchBox;
        myReplaceBox = replaceBox;
        myRegexCheck = regexCheck;
        myCaseSensitiveCheck = caseSensitiveCheck;
        myUseCurrentDateCheck = useCurrentDateCheck;
        myCaseBox = caseBox;
        myCounterStartBox = counterStartBox;
        myCounterStepBox = counterStepBox;
        myCounterWidthBox = counterWidthBox;

        TableLayout templates = TableLayout.create(StaticPosition.TOP);
        templates.add(Label.create(LocalizeValue.localizeTODO("Name template:")), TableLayout.cell(0, 0));
        templates.add(nameBox, TableLayout.cell(0, 1).fill());
        templates.add(Label.create(LocalizeValue.localizeTODO("Extension template:")), TableLayout.cell(0, 2));
        templates.add(extBox, TableLayout.cell(0, 3).fill());
        templates.add(Label.create(LocalizeValue.localizeTODO("Search for:")), TableLayout.cell(1, 0));
        templates.add(searchBox, TableLayout.cell(1, 1).fill());
        templates.add(Label.create(LocalizeValue.localizeTODO("Replace with:")), TableLayout.cell(1, 2));
        templates.add(replaceBox, TableLayout.cell(1, 3).fill());

        HorizontalLayout flags = HorizontalLayout.create(Space.MEDIUM);
        flags.add(regexCheck);
        flags.add(caseSensitiveCheck);
        flags.add(Label.create(LocalizeValue.localizeTODO("Case:")));
        flags.add(caseBox);

        HorizontalLayout counter = HorizontalLayout.create(Space.MEDIUM);
        counter.add(Label.create(LocalizeValue.localizeTODO("Counter — start:")));
        counter.add(counterStartBox);
        counter.add(Label.create(LocalizeValue.localizeTODO("step:")));
        counter.add(counterStepBox);
        counter.add(Label.create(LocalizeValue.localizeTODO("width:")));
        counter.add(counterWidthBox);
        counter.add(useCurrentDateCheck);

        VerticalLayout controls = VerticalLayout.create(Space.SMALL);
        controls.add(templates);
        controls.add(flags);
        controls.add(counter);
        controls.add(HtmlLabel.create(LocalizeValue.of(HINT)));
        controls.add(Label.create(LocalizeValue.localizeTODO(
            "Preview — conflicting rows are highlighted; rename is disabled while any conflict exists.")));

        Table<PreviewRow> table = Table.create(myPreviewModel);
        table.setRowBackgroundGetter(row -> row.conflict() ? ComponentColors.ERROR_BACKGROUND : null);
        table.addColumn(LocalizeValue.localizeTODO("Old name"), (PreviewRow row) -> row.entry().name())
            .setWidth(360)
            .setRenderer((presentation, value, row) -> presentation.append(row.entry().name(), attribute(row)));
        table.addColumn(LocalizeValue.localizeTODO("New name"), PreviewRow::newName)
            .setWidth(360)
            .setRenderer((presentation, value, row) -> presentation.append(row.newName(), attribute(row)));

        for (TextBox box : List.of(nameBox, extBox, searchBox, replaceBox)) {
            box.addValueListener(event -> recomputePreview());
        }
        for (CheckBox check : List.of(regexCheck, caseSensitiveCheck, useCurrentDateCheck)) {
            check.addValueListener(event -> recomputePreview());
        }
        caseBox.addValueListener(event -> recomputePreview());
        for (IntBox box : List.of(counterStartBox, counterStepBox, counterWidthBox)) {
            box.addValueListener(event -> recomputePreview());
        }

        recomputePreview();

        UiTask.backgroundThenUi(this::snapshotExistingNames, snapshot -> {
            myExistingNames = snapshot;
            // the snapshot can outlive a dialog closed while a slow share was still being listed
            if (!Disposer.isDisposed(uiDisposable)) {
                recomputePreview();
            }
        });

        return DockLayout.create(Space.SMALL).top(controls).center(ScrollableLayout.create(table));
    }

    private static TextAttribute attribute(PreviewRow row) {
        return row.conflict() ? TextAttribute.ERROR : TextAttribute.REGULAR;
    }

    private Map<Path, Set<String>> snapshotExistingNames() {
        Map<Path, Set<String>> snapshot = new HashMap<>();
        for (FileEntry entry : myEntries) {
            Path parent = entry.path().getParent();
            if (parent == null || snapshot.containsKey(parent)) {
                continue;
            }
            Set<String> names = new HashSet<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
                for (Path child : stream) {
                    Path fileName = child.getFileName();
                    if (fileName != null) {
                        names.add(foldName(fileName.toString()));
                    }
                }
            }
            catch (Exception e) {
                // Unreadable directory - treat every target in it as free.
            }
            snapshot.put(parent, names);
        }
        return snapshot;
    }

    private static String foldName(String name) {
        return SystemInfo.isWindows ? StringUtil.toLowerCase(name) : name;
    }

    @RequiredUIAccess
    private void recomputePreview() {
        ComboBox<CaseOption> caseBox = myCaseBox;
        CaseOption caseOption = caseBox != null ? caseBox.getValue() : null;

        MultiRenameTemplate.Options options = new MultiRenameTemplate.Options(
            text(myNameBox),
            text(myExtBox),
            text(mySearchBox),
            text(myReplaceBox),
            selected(myRegexCheck),
            selected(myCaseSensitiveCheck),
            caseOption != null ? caseOption.mode() : MultiRenameTemplate.CaseMode.UNCHANGED,
            new MultiRenameTemplate.CounterConfig(
                number(myCounterStartBox, 1),
                number(myCounterStepBox, 1),
                number(myCounterWidthBox, 1)),
            ZoneId.systemDefault(),
            selected(myUseCurrentDateCheck),
            null,
            new Random());

        List<MultiRenameTemplate.Input> inputs = new ArrayList<>(myEntries.size());
        List<Path> sources = new ArrayList<>(myEntries.size());
        for (FileEntry entry : myEntries) {
            inputs.add(MultiRenameTemplate.Input.from(entry.path(), entry.lastModified()));
            sources.add(entry.path());
        }

        List<String> targets = MultiRenameTemplate.render(inputs, options);
        Set<Integer> conflicts = MultiRenameTemplate.detectConflicts(sources, targets, SystemInfo.isWindows,
            this::existsOnDisk);
        myConflictRows = conflicts;
        myCurrentTargets = targets;

        List<PreviewRow> rows = new ArrayList<>(myEntries.size());
        for (int i = 0; i < myEntries.size(); i++) {
            rows.add(new PreviewRow(myEntries.get(i), targets.get(i), conflicts.contains(i)));
        }
        myPreviewModel.replaceAll(rows);
        updateOkButtonState();
    }

    private boolean existsOnDisk(Path target) {
        Map<Path, Set<String>> names = myExistingNames;
        if (names == null) {
            return false;
        }
        Path fileName = target.getFileName();
        if (fileName == null) {
            return false;
        }
        Set<String> inDirectory = names.get(target.getParent());
        return inDirectory != null && inDirectory.contains(foldName(fileName.toString()));
    }

    private List<Rename> collectRenames() {
        List<Rename> renames = new ArrayList<>();
        for (int i = 0; i < myEntries.size() && i < myCurrentTargets.size(); i++) {
            FileEntry entry = myEntries.get(i);
            String target = myCurrentTargets.get(i);
            if (!target.equals(entry.name())) {
                renames.add(new Rename(entry, target));
            }
        }
        return renames;
    }

    /** Nothing to rename, or a clash anywhere, leaves nothing for OK to do. */
    @Override
    public boolean doUpdateOkButtonState() {
        return myConflictRows.isEmpty() && !collectRenames().isEmpty();
    }

    @Override
    public Component getPreferredFocusedComponent() {
        TextBox nameBox = myNameBox;
        return nameBox != null ? nameBox : super.getPreferredFocusedComponent();
    }

    @Override
    public @Nullable WidthAndHeight getInitialSize() {
        // in font sizes: the controls take about sixteen, the preview gets the rest
        return WidthAndHeight.ofFont(60, 40);
    }

    @Override
    protected DialogOkAction createOkAction() {
        return new DialogOkAction(LocalizeValue.localizeTODO("Rename"));
    }

    /** The OK button follows the preview, but a frontend may let a click through before it re-reads the state. */
    @RequiredUIAccess
    @Override
    public boolean canHandle(AnAction action, @Nullable DialogValue value) {
        if (!(action instanceof DialogOkAction)) {
            return true;
        }
        if (!myConflictRows.isEmpty()) {
            Alerts.okError(LocalizeValue.localizeTODO("Some new names conflict. Resolve the highlighted rows first."))
                .showAsync();
            return false;
        }
        List<Rename> renames = collectRenames();
        if (renames.isEmpty()) {
            Alerts.okError(LocalizeValue.localizeTODO("No file name would change.")).showAsync();
            return false;
        }
        myResult = renames;
        return true;
    }

    private static String text(@Nullable TextBox box) {
        String value = box != null ? box.getValue() : null;
        return value != null ? value : "";
    }

    private static boolean selected(@Nullable CheckBox check) {
        Boolean value = check != null ? check.getValue() : null;
        return value != null && value;
    }

    /** An IntBox mid-edit can hold no number; the preview then renders with the default. */
    private static int number(@Nullable IntBox box, int fallback) {
        Integer value = box != null ? box.getValue() : null;
        return value != null ? value : fallback;
    }
}
