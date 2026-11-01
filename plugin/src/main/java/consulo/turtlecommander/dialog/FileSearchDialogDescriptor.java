package consulo.turtlecommander.dialog;

import consulo.application.ApplicationPropertiesComponent;
import consulo.application.util.SystemInfo;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.search.DateFilter;
import consulo.turtlecommander.search.DateFilterMode;
import consulo.turtlecommander.search.FileSearchCriteria;
import consulo.turtlecommander.search.NamePatternMode;
import consulo.turtlecommander.search.PermissionsFilter;
import consulo.turtlecommander.search.PermissionsFilterMode;
import consulo.turtlecommander.search.ResultKind;
import consulo.turtlecommander.search.SizeFilter;
import consulo.turtlecommander.search.SizeFilterMode;
import consulo.turtlecommander.search.SymlinkSearchFilter;
import consulo.turtlecommander.settings.TurtleCommanderSettings;
import consulo.turtlecommander.util.PermissionFlag;
import consulo.ui.Alerts;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.HasFocus;
import consulo.ui.HorizontalAlignment;
import consulo.ui.Label;
import consulo.ui.RadioButton;
import consulo.ui.RadioGroup;
import consulo.ui.Space;
import consulo.ui.Tab;
import consulo.ui.TextBox;
import consulo.ui.TextBoxWithHistory;
import consulo.ui.WidthAndHeight;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.DialogValue;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.TabbedLayout;
import consulo.ui.layout.VerticalLayout;
import org.jspecify.annotations.Nullable;

import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The full search form: name and content on the first page; size, dates, ownership and permissions on the second.
 * Only parses what was typed - the search itself touches the disk, off the UI thread.
 */
public final class FileSearchDialogDescriptor extends DialogDescriptor {
    /** Same property TextFieldWithStoredHistory used, so the content history survives the migration. */
    private static final String CONTENT_HISTORY_KEY = "TurtleCommander.contentSearchHistory";
    private static final int CONTENT_HISTORY_SIZE = 10;

    private static final String DATE_HINT = "yyyy[-MM[-dd[ HH:mm]]]";
    private static final String DATE_ERROR = "Invalid date — use yyyy, yyyy-MM, yyyy-MM-dd, or yyyy-MM-dd HH:mm";

    /** Accepted date inputs, longest first; the calendar field is the granularity each implies. */
    private static final Map<String, Integer> DATE_FORMATS = new LinkedHashMap<>();

    static {
        DATE_FORMATS.put("yyyy-MM-dd HH:mm", Calendar.MINUTE);
        DATE_FORMATS.put("yyyy-MM-dd", Calendar.DAY_OF_MONTH);
        DATE_FORMATS.put("yyyy-MM", Calendar.MONTH);
        DATE_FORMATS.put("yyyy", Calendar.YEAR);
    }

    private enum SizeUnit {
        B(1L), KB(1024L), MB(1024L * 1024), GB(1024L * 1024 * 1024);

        private final long myMultiplier;

        SizeUnit(long multiplier) {
            myMultiplier = multiplier;
        }
    }

    /** What a failed check reports, and where to send the user to fix it. */
    private record Problem(String message, HasFocus target, @Nullable Tab tab) {
    }

    private final Path myRoot;
    private final @Nullable FileSearchCriteria myInitial;
    // the search runs where the files are, so the attribute model is the host's
    private final boolean myHostWindows = SystemInfo.isWindows;
    private final List<PermissionFlag> myPermissionFlagsForOs =
        myHostWindows ? PermissionFlag.DOS_FLAGS : PermissionFlag.POSIX_FLAGS;

    private @Nullable Form myForm;
    private FileSearchCriteria myCriteria;

    public FileSearchDialogDescriptor(Path root, @Nullable FileSearchCriteria initial) {
        super(LocalizeValue.localizeTODO("Search Files"));
        myRoot = root;
        myInitial = initial;
        myCriteria = initial != null ? initial : defaultCriteria(root);
    }

    /** The criteria as the user left them - read once the dialog closed with OK; the initial ones before that. */
    public FileSearchCriteria getCriteria() {
        return myCriteria;
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        Form form = new Form();
        myForm = form;
        return form.myTabs;
    }

    @Override
    public Component getPreferredFocusedComponent() {
        Form form = myForm;
        return form != null ? form.myNameBox : super.getPreferredFocusedComponent();
    }

    @Override
    public @Nullable WidthAndHeight getInitialSize() {
        // in font sizes: the attributes page is the taller one; POSIX adds the legend and special-bit rows
        return WidthAndHeight.ofFont(56, myHostWindows ? 30 : 34);
    }

    @Override
    protected DialogOkAction createOkAction() {
        return new DialogOkAction(LocalizeValue.localizeTODO("Search"));
    }

    /**
     * An invalid glob/regex, size or date keeps the dialog open: once the results tab shows the error, the form is
     * already gone.
     */
    @RequiredUIAccess
    @Override
    public boolean canHandle(AnAction action, @Nullable DialogValue value) {
        if (!(action instanceof DialogOkAction)) {
            return true;
        }
        Form form = myForm;
        if (form == null) {
            return true;
        }
        Problem problem = form.validate();
        if (problem != null) {
            Tab tab = problem.tab();
            if (tab != null) {
                tab.select();
            }
            problem.target().focus();
            Alerts.okError(LocalizeValue.of(problem.message())).showAsync();
            return false;
        }
        myCriteria = form.buildCriteria();
        // only after validation, so fat-fingered patterns never reach the history dropdowns
        form.rememberHistory();
        return true;
    }

    /** The whole form; built on the UI thread once the dialog asks for its content. */
    private final class Form {
        private final TabbedLayout myTabs = TabbedLayout.create();
        private final Tab myGeneralTab;
        private final Tab myAttributesTab;

        private final TextBox myRootBox;

        private final CheckBox myNameCheck;
        private final TextBoxWithHistory myNameBox;
        private final RadioGroup<NamePatternMode> myModeGroup = RadioGroup.create();
        private final List<RadioButton> myModeButtons = new ArrayList<>();
        private final CheckBox myCaseSensitiveCheck;
        private final RadioGroup<ResultKind> myResultKindGroup = RadioGroup.create();
        private final List<RadioButton> myResultKindButtons = new ArrayList<>();
        private final ComboBox<SymlinkSearchFilter> mySymlinkBox;

        private final CheckBox myContentCheck;
        private final TextBoxWithHistory myContentBox;
        private final CheckBox myContentCaseSensitiveCheck;

        private final CheckBox mySizeCheck;
        private final ComboBox<SizeFilterMode> mySizeModeBox;
        private final TextBox mySizeBox1 = TextBox.create().withVisibleLength(10);
        private final TextBox mySizeBox2 = TextBox.create().withVisibleLength(10);
        private final Label mySizeAndLabel = Label.create(LocalizeValue.localizeTODO("and"));
        private final ComboBox<SizeUnit> mySizeUnitBox;

        private final DateSection myCreationDate;
        private final DateSection myModificationDate;

        private final CheckBox myOwnerCheck;
        private final TextBox myOwnerBox;
        private final CheckBox myGroupCheck;
        private final TextBox myGroupBox;

        private final CheckBox myPermissionsCheck;
        private final ComboBox<PermissionsFilterMode> myPermissionsModeBox;
        private final Map<PermissionFlag, CheckBox> myPermissionChecks = new LinkedHashMap<>();

        @RequiredUIAccess
        Form() {
            FileSearchCriteria initial = myInitial;

            myRootBox = TextBox.create(myRoot.toString());

            boolean regexp = initial != null && initial.namePatternMode() == NamePatternMode.REGEXP;
            myNameCheck = check("Search by name", initial == null || initial.namePattern() != null);
            String pattern = initial != null && initial.namePattern() != null ? initial.namePattern() : defaultPattern(regexp);
            myNameBox = TextBoxWithHistory.create(pattern);
            myNameBox.setHistory(TurtleCommanderSettings.getInstance().getNameSearchHistory(regexp));
            myModeButtons.add(myModeGroup.newButton(LocalizeValue.localizeTODO("Glob"), NamePatternMode.GLOB));
            myModeButtons.add(myModeGroup.newButton(LocalizeValue.localizeTODO("Regexp"), NamePatternMode.REGEXP));
            myModeGroup.setValue(regexp ? NamePatternMode.REGEXP : NamePatternMode.GLOB);
            myCaseSensitiveCheck = check("Case sensitive", initial != null && initial.caseSensitive());

            myResultKindButtons.add(myResultKindGroup.newButton(LocalizeValue.localizeTODO("All"), ResultKind.ALL));
            myResultKindButtons.add(myResultKindGroup.newButton(LocalizeValue.localizeTODO("Files"), ResultKind.FILES));
            myResultKindButtons.add(myResultKindGroup.newButton(LocalizeValue.localizeTODO("Dirs"), ResultKind.DIRS));
            myResultKindGroup.setValue(initial != null ? initial.resultKind() : ResultKind.ALL);
            mySymlinkBox = combo(SymlinkSearchFilter.values(), FileSearchDialogDescriptor::symlinkLabel,
                initial != null ? initial.symlinkFilter() : SymlinkSearchFilter.ANY);

            String content = initial != null ? initial.contentPattern() : null;
            myContentCheck = check("Search by content", content != null);
            myContentBox = TextBoxWithHistory.create(content != null ? content : "");
            myContentBox.setHistory(loadContentHistory());
            myContentCaseSensitiveCheck = check("Case sensitive", initial != null && initial.contentCaseSensitive());

            SizeFilter size = initial != null ? initial.sizeFilter() : null;
            mySizeCheck = check("Search by size", size != null);
            mySizeModeBox = combo(SizeFilterMode.values(), FileSearchDialogDescriptor::sizeModeLabel,
                size != null ? size.mode() : SizeFilterMode.MORE_THAN);
            mySizeUnitBox = combo(SizeUnit.values(), Enum::name, size != null ? bytesToUnit(size.sizeBytes()) : SizeUnit.MB);
            if (size != null) {
                mySizeBox1.setValue(bytesToUnitValue(size.sizeBytes()));
                Long size2 = size.sizeBytes2();
                if (size2 != null) {
                    mySizeBox2.setValue(bytesToUnitValue(size2));
                }
            }

            myCreationDate = new DateSection("Search by creation date",
                initial != null ? initial.creationDateFilter() : null);
            myModificationDate = new DateSection("Search by modification date",
                initial != null ? initial.modificationDateFilter() : null);

            String owner = initial != null ? initial.ownerPattern() : null;
            myOwnerCheck = check("Search by owner", owner != null);
            myOwnerBox = TextBox.create(owner != null ? owner : "");
            String group = initial != null ? initial.groupPattern() : null;
            myGroupCheck = check("Search by group", group != null);
            myGroupBox = TextBox.create(group != null ? group : "");

            PermissionsFilter permissions = initial != null ? initial.permissionsFilter() : null;
            myPermissionsCheck = check("Search by permissions", permissions != null);
            myPermissionsModeBox = combo(PermissionsFilterMode.values(), FileSearchDialogDescriptor::permissionsModeLabel,
                permissions != null ? permissions.mode() : PermissionsFilterMode.EXACT_MATCH);
            for (PermissionFlag flag : myPermissionFlagsForOs) {
                myPermissionChecks.put(flag, check(permissionLabel(flag), permissions != null && permissions.flags().contains(flag)));
            }

            myGeneralTab = myTabs.addTab(LocalizeValue.localizeTODO("Name and Content"), page(createGeneralPage()));
            myAttributesTab = myTabs.createTab();
            // the count keeps filters set on the hidden page from being forgotten, e.g. when editing a search
            myAttributesTab.setRenderer((tab, presentation) -> {
                int active = activeAttributeFilters();
                presentation.append(LocalizeValue.of(active == 0 ? "Attributes" : "Attributes (" + active + ")"));
            });
            myTabs.addTab(myAttributesTab, page(createAttributesPage()));

            setupEnabling();
            setupInBetween();
            setupNameHistorySwap();
        }

        @RequiredUIAccess
        private Component createGeneralPage() {
            VerticalLayout layout = VerticalLayout.create(Space.MEDIUM);

            layout.add(DockLayout.create(Space.SMALL)
                .left(Label.create(LocalizeValue.localizeTODO("Search in:")))
                .center(myRootBox));

            HorizontalLayout mode = HorizontalLayout.create(Space.SMALL);
            myModeButtons.forEach(mode::add);
            mode.add(myCaseSensitiveCheck);

            HorizontalLayout kind = HorizontalLayout.create(Space.SMALL);
            myResultKindButtons.forEach(kind::add);
            kind.add(Label.create(LocalizeValue.localizeTODO("Symlinks:")));
            kind.add(mySymlinkBox);

            VerticalLayout name = VerticalLayout.create(Space.SMALL);
            name.add(DockLayout.create().center(myNameBox));
            name.add(DockLayout.create().left(mode).right(kind));
            layout.add(section(myNameCheck, name));

            layout.add(section(myContentCheck, DockLayout.create(Space.SMALL)
                .center(myContentBox)
                .right(myContentCaseSensitiveCheck)));
            return layout;
        }

        @RequiredUIAccess
        private Component createAttributesPage() {
            VerticalLayout layout = VerticalLayout.create(Space.MEDIUM);

            HorizontalLayout size = HorizontalLayout.create(Space.SMALL);
            size.add(mySizeModeBox);
            size.add(mySizeBox1);
            size.add(mySizeAndLabel);
            size.add(mySizeBox2);
            size.add(mySizeUnitBox);
            layout.add(section(mySizeCheck, size));

            layout.add(section(myCreationDate.myCheck, myCreationDate.createRow()));
            layout.add(section(myModificationDate.myCheck, myModificationDate.createRow()));

            // owner and group share a row; Windows has no group to match against
            DockLayout ownership = DockLayout.create(Space.SMALL).left(myOwnerCheck);
            if (myHostWindows) {
                ownership.center(myOwnerBox);
            }
            else {
                ownership.center(DockLayout.create(Space.SMALL)
                    .center(myOwnerBox)
                    .right(DockLayout.create(Space.SMALL).left(myGroupCheck).center(myGroupBox)));
            }
            layout.add(ownership);

            HorizontalLayout permissions = HorizontalLayout.create(Space.MEDIUM);
            permissions.add(myPermissionsModeBox);
            if (myHostWindows) {
                myPermissionChecks.values().forEach(permissions::add);
            }
            else {
                permissions.add(createPosixFlags());
            }
            layout.add(section(myPermissionsCheck, permissions));
            return layout;
        }

        /**
         * Each rwx triplet with its legend underneath and its special bit under that: setuid under Owner, setgid
         * under Group, sticky under Others.
         */
        @RequiredUIAccess
        private Component createPosixFlags() {
            HorizontalLayout groups = HorizontalLayout.create(Space.LARGE);
            groups.add(posixGroup("Owner", PermissionFlag.OWNER_READ, PermissionFlag.OWNER_WRITE,
                PermissionFlag.OWNER_EXECUTE, PermissionFlag.SETUID));
            groups.add(posixGroup("Group", PermissionFlag.GROUP_READ, PermissionFlag.GROUP_WRITE,
                PermissionFlag.GROUP_EXECUTE, PermissionFlag.SETGID));
            groups.add(posixGroup("Others", PermissionFlag.OTHERS_READ, PermissionFlag.OTHERS_WRITE,
                PermissionFlag.OTHERS_EXECUTE, PermissionFlag.STICKY));
            return groups;
        }

        @RequiredUIAccess
        private Component posixGroup(String legend, PermissionFlag read, PermissionFlag write, PermissionFlag execute,
                                     PermissionFlag special) {
            HorizontalLayout rwx = HorizontalLayout.create(Space.SMALL);
            rwx.add(permissionCheck(read));
            rwx.add(permissionCheck(write));
            rwx.add(permissionCheck(execute));

            VerticalLayout group = VerticalLayout.create(Space.X_SMALL, HorizontalAlignment.CENTER);
            group.add(rwx);
            group.add(Label.create(LocalizeValue.localizeTODO(legend)));
            group.add(permissionCheck(special));
            return group;
        }

        private CheckBox permissionCheck(PermissionFlag flag) {
            CheckBox check = myPermissionChecks.get(flag);
            if (check == null) {
                throw new IllegalStateException("No checkbox for " + flag);
            }
            return check;
        }

        @RequiredUIAccess
        private void setupEnabling() {
            bindSection(myNameCheck, myNameBox, this::updateNameEnabled);
            bindSection(myContentCheck, myContentBox, () -> {
                boolean enabled = myContentCheck.getValueOrError();
                myContentBox.setEnabled(enabled);
                myContentCaseSensitiveCheck.setEnabled(enabled);
            });
            bindSection(mySizeCheck, mySizeBox1, () -> {
                boolean enabled = mySizeCheck.getValueOrError();
                mySizeModeBox.setEnabled(enabled);
                mySizeBox1.setEnabled(enabled);
                mySizeBox2.setEnabled(enabled);
                mySizeUnitBox.setEnabled(enabled);
            });
            myCreationDate.bind();
            myModificationDate.bind();
            bindSection(myOwnerCheck, myOwnerBox, () -> myOwnerBox.setEnabled(myOwnerCheck.getValueOrError()));
            bindSection(myGroupCheck, myGroupBox, () -> myGroupBox.setEnabled(myGroupCheck.getValueOrError()));
            bindSection(myPermissionsCheck, null, () -> {
                boolean enabled = myPermissionsCheck.getValueOrError();
                myPermissionsModeBox.setEnabled(enabled);
                myPermissionChecks.values().forEach(check -> check.setEnabled(enabled));
            });
        }

        @RequiredUIAccess
        private void updateNameEnabled() {
            boolean enabled = myNameCheck.getValueOrError();
            myNameBox.setEnabled(enabled);
            myModeButtons.forEach(button -> button.setEnabled(enabled));
            myCaseSensitiveCheck.setEnabled(enabled);
            myResultKindButtons.forEach(button -> button.setEnabled(enabled));
        }

        /** Wires a section checkbox to its enable pass and moves focus into the section when ticked. */
        @RequiredUIAccess
        private void bindSection(CheckBox check, @Nullable HasFocus focusTarget, Runnable updateEnabled) {
            check.addValueListener(event -> {
                updateEnabled.run();
                myAttributesTab.update();
                if (focusTarget != null && check.getValueOrError()) {
                    focusTarget.focus();
                }
            });
            updateEnabled.run();
        }

        @RequiredUIAccess
        private void setupInBetween() {
            Runnable update = () -> {
                boolean inBetween = mySizeModeBox.getValue() == SizeFilterMode.IN_BETWEEN;
                mySizeBox2.setVisible(inBetween);
                mySizeAndLabel.setVisible(inBetween);
            };
            // picking a mode or a unit means a bound is about to be typed
            mySizeModeBox.addValueListener(event -> {
                update.run();
                mySizeBox1.focus();
            });
            mySizeUnitBox.addValueListener(event -> mySizeBox1.focus());
            update.run();

            myContentCaseSensitiveCheck.addValueListener(event -> myContentBox.focus());
            myCaseSensitiveCheck.addValueListener(event -> myNameBox.focus());
            myResultKindGroup.addValueListener(kind -> myNameBox.focus());
        }

        /**
         * Glob and regex patterns have separate MRU histories, so toggling the mode swaps the dropdown. The typed
         * text stays, so an edit survives realising the wrong mode was picked.
         */
        @RequiredUIAccess
        private void setupNameHistorySwap() {
            myModeGroup.addValueListener(mode -> {
                myNameBox.setHistory(TurtleCommanderSettings.getInstance().getNameSearchHistory(mode == NamePatternMode.REGEXP));
                myNameBox.focus();
            });
        }

        private int activeAttributeFilters() {
            int count = 0;
            for (CheckBox check : List.of(mySizeCheck, myCreationDate.myCheck, myModificationDate.myCheck, myOwnerCheck,
                myGroupCheck, myPermissionsCheck)) {
                if (check == myGroupCheck && myHostWindows) {
                    continue;
                }
                if (selected(check)) {
                    count++;
                }
            }
            return count;
        }

        private boolean isRegexp() {
            return myModeGroup.getValue() == NamePatternMode.REGEXP;
        }

        private @Nullable Problem validate() {
            Problem problem = validateRoot();
            if (problem == null) {
                problem = validateName();
            }
            if (problem == null) {
                problem = validateSize();
            }
            if (problem == null) {
                problem = myCreationDate.validate();
            }
            if (problem == null) {
                problem = myModificationDate.validate();
            }
            return problem;
        }

        /** The root must be a syntactically valid path - otherwise {@code Path.of} throws later. */
        private @Nullable Problem validateRoot() {
            String text = text(myRootBox);
            if (text.isEmpty()) {
                return new Problem("Enter a folder to search in", myRootBox, myGeneralTab);
            }
            try {
                Path.of(text);
                return null;
            }
            catch (InvalidPathException e) {
                return new Problem("Invalid folder path: " + (e.getReason() != null ? e.getReason() : text), myRootBox,
                    myGeneralTab);
            }
        }

        private @Nullable Problem validateName() {
            if (!selected(myNameCheck)) {
                return null;
            }
            String pattern = text(myNameBox);
            if (pattern.isEmpty()) {
                return null;
            }
            try {
                if (isRegexp()) {
                    Pattern.compile(pattern);
                }
                else {
                    FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                }
                return null;
            }
            catch (Exception e) {
                String kind = isRegexp() ? "regex" : "glob";
                return new Problem("Invalid " + kind + ": " + (e.getMessage() != null ? e.getMessage() : pattern), myNameBox,
                    myGeneralTab);
            }
        }

        /** A non-numeric size would silently become 0 ("more than 0 B" matches everything). */
        private @Nullable Problem validateSize() {
            if (!selected(mySizeCheck)) {
                return null;
            }
            Problem first = validateSizeBox(mySizeBox1);
            if (first != null || mySizeModeBox.getValue() != SizeFilterMode.IN_BETWEEN) {
                return first;
            }
            return validateSizeBox(mySizeBox2);
        }

        private @Nullable Problem validateSizeBox(TextBox box) {
            String text = text(box);
            if (text.isEmpty()) {
                return new Problem("Enter a size", box, myAttributesTab);
            }
            Double value = parseDouble(text);
            if (value == null) {
                return new Problem("Size must be a number", box, myAttributesTab);
            }
            if (value < 0) {
                return new Problem("Size cannot be negative", box, myAttributesTab);
            }
            return null;
        }

        private FileSearchCriteria buildCriteria() {
            String name = text(myNameBox);
            String content = text(myContentBox);
            String owner = text(myOwnerBox);
            String group = text(myGroupBox);
            NamePatternMode mode = myModeGroup.getValue();
            ResultKind kind = myResultKindGroup.getValue();
            SymlinkSearchFilter symlinks = mySymlinkBox.getValue();
            return new FileSearchCriteria(
                Path.of(text(myRootBox)),
                selected(myNameCheck) && !name.isEmpty() ? name : null,
                mode != null ? mode : NamePatternMode.GLOB,
                selected(myCaseSensitiveCheck),
                kind != null ? kind : ResultKind.ALL,
                selected(myContentCheck) && !content.isEmpty() ? content : null,
                selected(myContentCaseSensitiveCheck),
                buildSizeFilter(),
                myCreationDate.build(),
                myModificationDate.build(),
                selected(myOwnerCheck) && !owner.isEmpty() ? owner : null,
                !myHostWindows && selected(myGroupCheck) && !group.isEmpty() ? group : null,
                buildPermissionsFilter(),
                symlinks != null ? symlinks : SymlinkSearchFilter.ANY);
        }

        private @Nullable SizeFilter buildSizeFilter() {
            if (!selected(mySizeCheck)) {
                return null;
            }
            SizeFilterMode mode = mySizeModeBox.getValue();
            if (mode == null) {
                mode = SizeFilterMode.MORE_THAN;
            }
            SizeUnit unit = mySizeUnitBox.getValue();
            long multiplier = unit != null ? unit.myMultiplier : 1L;
            long size1 = sizeBytes(mySizeBox1, multiplier);
            Long size2 = mode == SizeFilterMode.IN_BETWEEN ? sizeBytes(mySizeBox2, multiplier) : null;
            return new SizeFilter(mode, size1, size2);
        }

        private @Nullable PermissionsFilter buildPermissionsFilter() {
            if (!selected(myPermissionsCheck)) {
                return null;
            }
            PermissionsFilterMode mode = myPermissionsModeBox.getValue();
            Set<PermissionFlag> flags = new LinkedHashSet<>();
            myPermissionChecks.forEach((flag, check) -> {
                if (selected(check)) {
                    flags.add(flag);
                }
            });
            return new PermissionsFilter(mode != null ? mode : PermissionsFilterMode.EXACT_MATCH, flags);
        }

        private void rememberHistory() {
            String name = text(myNameBox);
            if (selected(myNameCheck) && !name.isEmpty()) {
                TurtleCommanderSettings.getInstance().addNameSearchHistory(name, isRegexp());
            }
            String content = text(myContentBox);
            if (selected(myContentCheck) && !content.isEmpty()) {
                saveContentHistory(content);
            }
        }

        /** One of the two date filters: a mode, a bound and, for "in between", a second bound. */
        private final class DateSection {
            private final CheckBox myCheck;
            private final ComboBox<DateFilterMode> myModeBox;
            private final TextBox myBox1 = dateBox();
            private final TextBox myBox2 = dateBox();
            private final Label myAndLabel = Label.create(LocalizeValue.localizeTODO("and"));

            @RequiredUIAccess
            DateSection(String title, @Nullable DateFilter filter) {
                myCheck = check(title, filter != null);
                myModeBox = combo(DateFilterMode.values(), FileSearchDialogDescriptor::dateModeLabel,
                    filter != null ? filter.mode() : DateFilterMode.EARLIER);
                if (filter != null) {
                    myBox1.setValue(filter.text1());
                    String text2 = filter.text2();
                    if (text2 != null) {
                        myBox2.setValue(text2);
                    }
                }
            }

            @RequiredUIAccess
            Component createRow() {
                HorizontalLayout row = HorizontalLayout.create(Space.SMALL);
                row.add(myModeBox);
                row.add(myBox1);
                row.add(myAndLabel);
                row.add(myBox2);
                return row;
            }

            @RequiredUIAccess
            void bind() {
                Runnable updateEnabled = () -> {
                    boolean enabled = myCheck.getValueOrError();
                    myModeBox.setEnabled(enabled);
                    myBox1.setEnabled(enabled);
                    myBox2.setEnabled(enabled);
                };
                Runnable updateVisibility = () -> {
                    boolean inBetween = myModeBox.getValue() == DateFilterMode.IN_BETWEEN;
                    myBox2.setVisible(inBetween);
                    myAndLabel.setVisible(inBetween);
                };
                myModeBox.addValueListener(event -> {
                    updateVisibility.run();
                    myBox1.focus();
                });
                updateVisibility.run();
                myCheck.addValueListener(event -> {
                    updateEnabled.run();
                    myAttributesTab.update();
                    if (myCheck.getValueOrError()) {
                        myBox1.focus();
                    }
                });
                updateEnabled.run();
            }

            /** An unparseable date would be dropped silently, running the search with no date filter. */
            @Nullable Problem validate() {
                if (!selected(myCheck)) {
                    return null;
                }
                if (parseDateRange(text(myBox1)) == null) {
                    return new Problem(DATE_ERROR, myBox1, myAttributesTab);
                }
                if (myModeBox.getValue() == DateFilterMode.IN_BETWEEN && parseDateRange(text(myBox2)) == null) {
                    return new Problem(DATE_ERROR, myBox2, myAttributesTab);
                }
                return null;
            }

            @Nullable DateFilter build() {
                if (!selected(myCheck)) {
                    return null;
                }
                DateFilterMode mode = myModeBox.getValue();
                if (mode == null) {
                    mode = DateFilterMode.EARLIER;
                }
                String text1 = text(myBox1);
                long[] range1 = parseDateRange(text1);
                if (range1 == null) {
                    return null;
                }
                String text2 = text(myBox2);
                long[] range2 = null;
                if (mode == DateFilterMode.IN_BETWEEN) {
                    range2 = parseDateRange(text2);
                    if (range2 == null) {
                        return null;
                    }
                }
                return new DateFilter(mode, range1[0], range1[1],
                    range2 != null ? range2[0] : null,
                    range2 != null ? range2[1] : null,
                    text1,
                    mode == DateFilterMode.IN_BETWEEN ? text2 : null);
            }
        }
    }

    /**
     * Splits a byte count into the largest unit that keeps it above 1, for round-tripping the form. Formatted
     * without the locale, so a decimal comma never fails to parse back.
     */
    public static String bytesToUnitValue(long bytes) {
        SizeUnit unit = bytesToUnit(bytes);
        if (unit == SizeUnit.B) {
            return String.valueOf(bytes);
        }
        return String.format(Locale.ROOT, "%.1f", bytes / (double) unit.myMultiplier);
    }

    public static String bytesToUnitName(long bytes) {
        return bytesToUnit(bytes).name();
    }

    private static SizeUnit bytesToUnit(long bytes) {
        if (bytes >= SizeUnit.GB.myMultiplier) {
            return SizeUnit.GB;
        }
        if (bytes >= SizeUnit.MB.myMultiplier) {
            return SizeUnit.MB;
        }
        if (bytes >= SizeUnit.KB.myMultiplier) {
            return SizeUnit.KB;
        }
        return SizeUnit.B;
    }

    /**
     * Expands a partial date into the window it denotes: "2024" covers the whole year, "2024-03" the whole month, and
     * so on. Returns {@code null} when nothing parses cleanly, which is what validation reports on. A DatePicker
     * cannot express these partial dates, so the bounds stay text.
     */
    static long @Nullable [] parseDateRange(String text) {
        if (text.isBlank()) {
            return null;
        }
        for (Map.Entry<String, Integer> format : DATE_FORMATS.entrySet()) {
            SimpleDateFormat formatter = new SimpleDateFormat(format.getKey());
            formatter.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = formatter.parse(text, position);
            if (parsed == null || position.getIndex() != text.length()) {
                continue;
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(parsed);
            long start = calendar.getTimeInMillis();
            calendar.add(format.getValue(), 1);
            calendar.add(Calendar.MILLISECOND, -1);
            return new long[]{start, calendar.getTimeInMillis()};
        }
        return null;
    }

    /** What OK-less callers see: a name-only glob search for everything under the root. */
    private static FileSearchCriteria defaultCriteria(Path root) {
        return new FileSearchCriteria(root, defaultPattern(false), NamePatternMode.GLOB, false, ResultKind.ALL, null, false,
            null, null, null, null, null, null, SymlinkSearchFilter.ANY);
    }

    private static String defaultPattern(boolean regexp) {
        return regexp ? ".*" : "*";
    }

    /** Most recent first, as TextFieldWithStoredHistory kept it: newline-separated in the application properties. */
    private static List<String> loadContentHistory() {
        String stored = ApplicationPropertiesComponent.getInstance().getValue(CONTENT_HISTORY_KEY);
        List<String> history = new ArrayList<>();
        if (stored != null) {
            for (String item : stored.split("\n")) {
                if (!item.isEmpty()) {
                    history.add(item);
                }
            }
        }
        return history;
    }

    private static void saveContentHistory(String text) {
        List<String> history = loadContentHistory();
        history.remove(text);
        history.add(0, text);
        while (history.size() > CONTENT_HISTORY_SIZE) {
            history.remove(history.size() - 1);
        }
        ApplicationPropertiesComponent.getInstance().setValue(CONTENT_HISTORY_KEY, String.join("\n", history));
    }

    @RequiredUIAccess
    private static TextBox dateBox() {
        TextBox box = TextBox.create().withVisibleLength(16);
        box.setPlaceholder(LocalizeValue.of(DATE_HINT));
        return box;
    }

    /** A section checkbox over its controls, which are indented under it. */
    @RequiredUIAccess
    private static Component section(CheckBox check, Component content) {
        content.paddingBuilder().leftSet(Space.LARGE).apply();
        VerticalLayout layout = VerticalLayout.create(Space.X_SMALL);
        layout.add(check);
        layout.add(content);
        return layout;
    }

    /** Each page scrolls on its own, so a small window still reaches every option. */
    @RequiredUIAccess
    private static Component page(Component content) {
        content.paddingBuilder().allSet(Space.SMALL).apply();
        return ScrollableLayout.create(content);
    }

    @RequiredUIAccess
    private static CheckBox check(String text, boolean selected) {
        return CheckBox.create(LocalizeValue.localizeTODO(text), selected);
    }

    @RequiredUIAccess
    private static <E> ComboBox<E> combo(E[] values, Function<E, String> label, E selected) {
        ComboBox<E> box = ComboBox.create(values);
        box.setTextRenderer(value -> value == null ? LocalizeValue.empty() : LocalizeValue.localizeTODO(label.apply(value)));
        box.setValue(selected);
        return box;
    }

    private static String symlinkLabel(SymlinkSearchFilter filter) {
        return switch (filter) {
            case ANY -> "Any";
            case ONLY -> "Only symlinks";
            case EXCLUDE -> "Exclude symlinks";
        };
    }

    private static String sizeModeLabel(SizeFilterMode mode) {
        return switch (mode) {
            case MORE_THAN -> "More than";
            case APPROX_EQUAL -> "Approximately equal";
            case LESS_THAN -> "Less than";
            case IN_BETWEEN -> "In between";
        };
    }

    private static String dateModeLabel(DateFilterMode mode) {
        return switch (mode) {
            case EARLIER -> "Earlier than";
            case APPROX_EQUAL -> "Approximately equal";
            case LATER -> "Later than";
            case IN_BETWEEN -> "In between";
        };
    }

    private static String permissionsModeLabel(PermissionsFilterMode mode) {
        return switch (mode) {
            case EXACT_MATCH -> "Exact match";
            case ALL_OF -> "All of";
            case ANY_OF -> "Any of";
            case NONE_OF -> "None of";
        };
    }

    private static String permissionLabel(PermissionFlag flag) {
        return switch (flag) {
            case OWNER_READ, GROUP_READ, OTHERS_READ -> "r";
            case OWNER_WRITE, GROUP_WRITE, OTHERS_WRITE -> "w";
            case OWNER_EXECUTE, GROUP_EXECUTE, OTHERS_EXECUTE -> "x";
            case SETUID -> "setuid";
            case SETGID -> "setgid";
            case STICKY -> "sticky";
            case DOS_READ_ONLY -> "Read-only";
            case DOS_HIDDEN -> "Hidden";
            case DOS_SYSTEM -> "System";
            case DOS_ARCHIVE -> "Archive";
        };
    }

    private static String text(TextBox box) {
        String value = box.getValue();
        return value != null ? value.trim() : "";
    }

    private static boolean selected(CheckBox check) {
        Boolean value = check.getValue();
        return value != null && value;
    }

    /** Scaled before truncating, so "1.5" MB - which is also how a reopened search shows 1.5 MB - stays 1.5 MB. */
    private static long sizeBytes(TextBox box, long multiplier) {
        Double value = parseDouble(text(box));
        return value != null ? (long) (value * multiplier) : 0L;
    }

    private static @Nullable Double parseDouble(String text) {
        try {
            return Double.valueOf(text);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }
}
