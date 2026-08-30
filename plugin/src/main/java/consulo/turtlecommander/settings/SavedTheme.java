package consulo.turtlecommander.settings;

import java.awt.Font;

/**
 * Flattened, serializable form of {@link Theme}. The XML serializer binds public mutable fields
 * on a class with a no-arg constructor, so the nested style objects are spread out by prefix.
 */
public class SavedTheme {
    public String name = "";

    public String panelFontFamily = "";
    public int panelFontSize = 0;
    public int panelFontStyle = Font.PLAIN;
    public String panelFontColor = "";
    public String panelBackgroundColor = "";
    public String panelSelectedColor = "";
    public String panelActiveSelectedColor = "";

    public String tabFontFamily = "";
    public int tabFontSize = 0;
    public int tabFontStyle = Font.PLAIN;
    public String tabFontColor = "";
    public String tabBackgroundColor = "";

    public String pathBarFontFamily = "";
    public int pathBarFontSize = 0;
    public int pathBarFontStyle = Font.PLAIN;
    public String pathBarFontColor = "";
    public String pathBarBackgroundColor = "";

    public String statusBarFontFamily = "";
    public int statusBarFontSize = 0;
    public int statusBarFontStyle = Font.PLAIN;
    public String statusBarFontColor = "";
    public String statusBarBackgroundColor = "";

    public String commandBarFontFamily = "";
    public int commandBarFontSize = 0;
    public int commandBarFontStyle = Font.PLAIN;
    public String commandBarFontColor = "";
    public String commandBarBackgroundColor = "";

    public String commandButtonFontFamily = "";
    public int commandButtonFontSize = 0;
    public int commandButtonFontStyle = Font.PLAIN;
    public String commandButtonFontColor = "";
    public String commandButtonBackgroundColor = "";

    public String driveSelectorFontFamily = "";
    public int driveSelectorFontSize = 0;
    public int driveSelectorFontStyle = Font.PLAIN;
    public String driveSelectorFontColor = "";
    public String driveSelectorBackgroundColor = "";

    public String columnHeaderFontFamily = "";
    public int columnHeaderFontSize = 0;
    public int columnHeaderFontStyle = Font.PLAIN;
    public String columnHeaderFontColor = "";
    public String columnHeaderBackgroundColor = "";

    public Theme toTheme() {
        return new Theme(
            name,
            new ThemeStyle(panelFontFamily, panelFontSize, panelFontStyle, panelFontColor,
                panelBackgroundColor, panelSelectedColor, panelActiveSelectedColor),
            new ThemeStyle(tabFontFamily, tabFontSize, tabFontStyle, tabFontColor, tabBackgroundColor, "", ""),
            new ThemeStyle(pathBarFontFamily, pathBarFontSize, pathBarFontStyle, pathBarFontColor, pathBarBackgroundColor, "", ""),
            new ThemeStyle(statusBarFontFamily, statusBarFontSize, statusBarFontStyle, statusBarFontColor, statusBarBackgroundColor, "", ""),
            new ThemeStyle(commandBarFontFamily, commandBarFontSize, commandBarFontStyle, commandBarFontColor, commandBarBackgroundColor, "", ""),
            new ThemeStyle(commandButtonFontFamily, commandButtonFontSize, commandButtonFontStyle, commandButtonFontColor, commandButtonBackgroundColor, "", ""),
            new ThemeStyle(driveSelectorFontFamily, driveSelectorFontSize, driveSelectorFontStyle, driveSelectorFontColor, driveSelectorBackgroundColor, "", ""),
            new ThemeStyle(columnHeaderFontFamily, columnHeaderFontSize, columnHeaderFontStyle, columnHeaderFontColor, columnHeaderBackgroundColor, "", "")
        );
    }

    public static SavedTheme fromTheme(Theme theme) {
        SavedTheme saved = new SavedTheme();
        saved.name = theme.name();

        ThemeStyle panel = theme.panelStyle();
        saved.panelFontFamily = panel.fontFamily();
        saved.panelFontSize = panel.fontSize();
        saved.panelFontStyle = panel.fontStyle();
        saved.panelFontColor = panel.fontColor();
        saved.panelBackgroundColor = panel.backgroundColor();
        saved.panelSelectedColor = panel.selectedColor();
        saved.panelActiveSelectedColor = panel.activeSelectedColor();

        ThemeStyle tab = theme.tabStyle();
        saved.tabFontFamily = tab.fontFamily();
        saved.tabFontSize = tab.fontSize();
        saved.tabFontStyle = tab.fontStyle();
        saved.tabFontColor = tab.fontColor();
        saved.tabBackgroundColor = tab.backgroundColor();

        ThemeStyle pathBar = theme.pathBarStyle();
        saved.pathBarFontFamily = pathBar.fontFamily();
        saved.pathBarFontSize = pathBar.fontSize();
        saved.pathBarFontStyle = pathBar.fontStyle();
        saved.pathBarFontColor = pathBar.fontColor();
        saved.pathBarBackgroundColor = pathBar.backgroundColor();

        ThemeStyle statusBar = theme.statusBarStyle();
        saved.statusBarFontFamily = statusBar.fontFamily();
        saved.statusBarFontSize = statusBar.fontSize();
        saved.statusBarFontStyle = statusBar.fontStyle();
        saved.statusBarFontColor = statusBar.fontColor();
        saved.statusBarBackgroundColor = statusBar.backgroundColor();

        ThemeStyle commandBar = theme.commandBarStyle();
        saved.commandBarFontFamily = commandBar.fontFamily();
        saved.commandBarFontSize = commandBar.fontSize();
        saved.commandBarFontStyle = commandBar.fontStyle();
        saved.commandBarFontColor = commandBar.fontColor();
        saved.commandBarBackgroundColor = commandBar.backgroundColor();

        ThemeStyle commandButton = theme.commandButtonStyle();
        saved.commandButtonFontFamily = commandButton.fontFamily();
        saved.commandButtonFontSize = commandButton.fontSize();
        saved.commandButtonFontStyle = commandButton.fontStyle();
        saved.commandButtonFontColor = commandButton.fontColor();
        saved.commandButtonBackgroundColor = commandButton.backgroundColor();

        ThemeStyle driveSelector = theme.driveSelectorStyle();
        saved.driveSelectorFontFamily = driveSelector.fontFamily();
        saved.driveSelectorFontSize = driveSelector.fontSize();
        saved.driveSelectorFontStyle = driveSelector.fontStyle();
        saved.driveSelectorFontColor = driveSelector.fontColor();
        saved.driveSelectorBackgroundColor = driveSelector.backgroundColor();

        ThemeStyle columnHeader = theme.columnHeaderStyle();
        saved.columnHeaderFontFamily = columnHeader.fontFamily();
        saved.columnHeaderFontSize = columnHeader.fontSize();
        saved.columnHeaderFontStyle = columnHeader.fontStyle();
        saved.columnHeaderFontColor = columnHeader.fontColor();
        saved.columnHeaderBackgroundColor = columnHeader.backgroundColor();

        return saved;
    }
}
