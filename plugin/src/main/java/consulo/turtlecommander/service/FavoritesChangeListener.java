package consulo.turtlecommander.service;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicAPI;

@TopicAPI(ComponentScope.PROJECT)
public interface FavoritesChangeListener {
    void favoritesChanged();
}
