package consulo.turtlecommander.settings;

import java.util.Objects;

public class StyleSet {
    public ComponentStyle panelStyle = new ComponentStyle();
    public ComponentStyle tabStyle = new ComponentStyle();
    public ComponentStyle pathBarStyle = new ComponentStyle();
    public ComponentStyle statusBarStyle = new ComponentStyle();
    public ComponentStyle commandBarStyle = new ComponentStyle();
    public ComponentStyle commandButtonStyle = new ComponentStyle();
    public ComponentStyle driveSelectorStyle = new ComponentStyle();
    public ComponentStyle columnHeaderStyle = new ComponentStyle();

    public void reset() {
        panelStyle = new ComponentStyle();
        tabStyle = new ComponentStyle();
        pathBarStyle = new ComponentStyle();
        statusBarStyle = new ComponentStyle();
        commandBarStyle = new ComponentStyle();
        commandButtonStyle = new ComponentStyle();
        driveSelectorStyle = new ComponentStyle();
        columnHeaderStyle = new ComponentStyle();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StyleSet other)) {
            return false;
        }
        return panelStyle.equals(other.panelStyle)
            && tabStyle.equals(other.tabStyle)
            && pathBarStyle.equals(other.pathBarStyle)
            && statusBarStyle.equals(other.statusBarStyle)
            && commandBarStyle.equals(other.commandBarStyle)
            && commandButtonStyle.equals(other.commandButtonStyle)
            && driveSelectorStyle.equals(other.driveSelectorStyle)
            && columnHeaderStyle.equals(other.columnHeaderStyle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(panelStyle, tabStyle, pathBarStyle, statusBarStyle,
            commandBarStyle, commandButtonStyle, driveSelectorStyle, columnHeaderStyle);
    }
}
