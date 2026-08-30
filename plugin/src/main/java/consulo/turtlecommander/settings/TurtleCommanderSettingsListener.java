package consulo.turtlecommander.settings;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicAPI;

@TopicAPI(ComponentScope.APPLICATION)
public interface TurtleCommanderSettingsListener {
    void settingsChanged();
}
