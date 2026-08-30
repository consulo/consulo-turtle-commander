module consulo.turtle.commander {
    requires consulo.annotation;
    requires consulo.application.api;
    requires consulo.project.api;
    requires consulo.project.ui.api;
    requires consulo.localize.api;
    requires consulo.ui.api;
    requires consulo.ui.ex.api;
    requires consulo.ui.ex.awt.api;
    requires consulo.configurable.api;
    requires consulo.virtual.file.system.api;
    requires consulo.file.editor.api;
    requires consulo.diff.api;
    requires consulo.ide.api;
    requires consulo.file.chooser.api;
    requires consulo.base.icon.library;
    requires consulo.util.jna;

    requires java.desktop;

    // Supplied by the platform, not bundled - see the pom for why a second copy breaks resolution.
    // Of the other archive libraries, cab4j and zstd-jni carry a real module-info, while junrar and
    // isofilereader carry neither that nor an Automatic-Module-Name, so they resolve as automatic
    // modules named after their jars. The pom's --add-reads=ALL-UNNAMED covers whatever the
    // compiler leaves on the classpath.
    requires org.apache.commons.compress;

    exports consulo.turtlecommander.action;
    exports consulo.turtlecommander.dialog;
    exports consulo.turtlecommander.filetype;
    exports consulo.turtlecommander.model;
    exports consulo.turtlecommander.operation;
    exports consulo.turtlecommander.search;
    exports consulo.turtlecommander.service;
    exports consulo.turtlecommander.settings;
    exports consulo.turtlecommander.ui;
    exports consulo.turtlecommander.util;
    exports consulo.turtlecommander.vfs;

    // The persisted state beans are reflected over by the serializer: TurtleCommanderSettings.State
    // with its nested style/column/rule beans in settings, and FileManagerState with PanelState,
    // TabState and FavoriteEntry in service.
    opens consulo.turtlecommander.settings to consulo.util.xml.serializer;
    opens consulo.turtlecommander.service to consulo.util.xml.serializer;
}
