package consulo.turtlecommander.settings;

import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.Button;
import consulo.ui.ButtonStyle;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.ComponentItemRenderer;
import consulo.ui.Space;
import consulo.ui.Table;
import consulo.ui.TableItemEditor;
import consulo.ui.ValueComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Column visibility, order and per-column styling: a table of columns with a visibility checkbox and Up/Down on the
 * left, and the selected column's style controls on the right.
 */
public class ColumnsEditor {
    private static final int LIST_WIDTH = 200;
    private static final int PANEL_HEIGHT = 260;

    /** One row: the column's identity and visibility, plus its own style editor. */
    private static final class ColumnRow {
        private final String myId;
        private final ComponentStyleEditor myStyleEditor;
        private boolean myVisible;

        ColumnRow(String id, boolean visible, ComponentStyleEditor styleEditor) {
            myId = id;
            myVisible = visible;
            myStyleEditor = styleEditor;
        }
    }

    /** The rows in their current order - mirrored by the table model, kept as a list for index-based comparison. */
    private final List<ColumnRow> myRows = new ArrayList<>();
    private final MutableFlatDataModel<ColumnRow> myModel = FlatDataModel.of(List.of());
    private final Table<ColumnRow> myTable = Table.create(myModel);
    private final DockLayout myDetailPanel = DockLayout.create();
    private final Component myRoot;

    @RequiredUIAccess
    public ColumnsEditor(List<ColumnConfig> initialColumns) {
        myTable.setAllowMultipleSelect(false);
        myTable.addColumn(LocalizeValue.localizeTODO("Visible"), row -> row.myVisible)
            .setWidth(60)
            .setRenderer(ComponentItemRenderer.reusable(
                () -> CheckBox.create(LocalizeValue.empty()),
                (checkBox, item) -> checkBox.setValue(Boolean.TRUE.equals(item.getValue()))))
            .setEditor(new TableItemEditor<>() {
                @RequiredUIAccess
                @Override
                public ValueComponent<Boolean> createComponent(ColumnRow row) {
                    return CheckBox.create(LocalizeValue.empty(), row.myVisible);
                }

                @RequiredUIAccess
                @Override
                public void commit(ColumnRow row, @Nullable Boolean value) {
                    row.myVisible = Boolean.TRUE.equals(value);
                    myModel.update(row);
                }
            });
        myTable.addColumn(LocalizeValue.localizeTODO("Column"), row -> row.myId);
        myTable.addSelectListener(event -> updateDetail());

        myRoot = buildPanel();
        resetFrom(initialColumns);
    }

    public Component getComponent() {
        return myRoot;
    }

    @RequiredUIAccess
    private Component buildPanel() {
        HorizontalLayout toolbar = HorizontalLayout.create(Space.X_SMALL);
        toolbar.add(toolbarButton(PlatformIconGroup.actionsMoveup(), "Move Up", () -> move(-1)));
        toolbar.add(toolbarButton(PlatformIconGroup.actionsMovedown(), "Move Down", () -> move(1)));

        ScrollableLayout tableScroll = ScrollableLayout.create(myTable);
        DockLayout listPanel = DockLayout.create(Space.X_SMALL).top(toolbar).center(tableScroll);
        listPanel.setWidth(LIST_WIDTH);

        DockLayout panel = DockLayout.create(Space.MEDIUM).left(listPanel).center(myDetailPanel);
        panel.setHeight(PANEL_HEIGHT);
        return LabeledLayout.create(LocalizeValue.localizeTODO("Columns"), panel);
    }

    @RequiredUIAccess
    private static Button toolbarButton(Image icon, String tooltip, Runnable action) {
        Button button = Button.create(LocalizeValue.empty());
        button.setIcon(icon);
        button.addStyle(ButtonStyle.BORDERLESS);
        button.setToolTipText(LocalizeValue.localizeTODO(tooltip));
        button.addClickListener(event -> action.run());
        return button;
    }

    @RequiredUIAccess
    private void move(int offset) {
        ColumnRow selected = myTable.getSelectedItem();
        int index = selected == null ? -1 : myRows.indexOf(selected);
        int target = index + offset;
        if (index < 0 || target < 0 || target >= myRows.size()) {
            return;
        }
        myRows.add(target, myRows.remove(index));
        myModel.remove(selected);
        myModel.add(selected, target);
        myTable.select(selected);
        updateDetail();
    }

    /** Shows the selected column's style controls; blank when nothing is selected. */
    @RequiredUIAccess
    private void updateDetail() {
        myDetailPanel.removeAll();
        ColumnRow selected = myTable.getSelectedItem();
        if (selected != null) {
            myDetailPanel.center(selected.myStyleEditor.getComponent());
        }
        myDetailPanel.forceRepaint();
    }

    @RequiredUIAccess
    public void resetFrom(List<ColumnConfig> columns) {
        ColumnRow previous = myTable.getSelectedItem();
        int previousIndex = previous == null ? -1 : myRows.indexOf(previous);

        myRows.clear();
        for (ColumnConfig column : columns) {
            myRows.add(new ColumnRow(column.id, column.visible, new ComponentStyleEditor(column.id, column.style)));
        }
        myModel.replaceAll(new ArrayList<>(myRows));

        // the rows are new objects, so the selection is carried over by position
        if (!myRows.isEmpty()) {
            int index = previousIndex >= 0 && previousIndex < myRows.size() ? previousIndex : 0;
            myTable.select(myRows.get(index));
        }
        updateDetail();
    }

    public void applyTo(TurtleCommanderSettings.State settings) {
        settings.columns.clear();
        for (ColumnRow row : myRows) {
            ColumnConfig config = new ColumnConfig();
            config.id = row.myId;
            config.visible = row.myVisible;
            row.myStyleEditor.applyTo(config.style);
            settings.columns.add(config);
        }
    }

    public boolean isModified(List<ColumnConfig> savedColumns) {
        if (myRows.size() != savedColumns.size()) {
            return true;
        }
        for (int i = 0; i < myRows.size(); i++) {
            ColumnRow row = myRows.get(i);
            ColumnConfig saved = savedColumns.get(i);
            if (!row.myId.equals(saved.id) || row.myVisible != saved.visible
                || row.myStyleEditor.isModified(saved.style)) {
                return true;
            }
        }
        return false;
    }
}
