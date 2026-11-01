package consulo.turtlecommander.dialog;

import consulo.container.plugin.PluginDescriptor;
import consulo.container.plugin.PluginManager;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.turtlecommander.icon.TurtleCommanderIconGroup;
import consulo.ui.AdvancedLabel;
import consulo.ui.Component;
import consulo.ui.ImageBox;
import consulo.ui.Label;
import consulo.ui.Space;
import consulo.ui.TextAttribute;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.image.ImageEffects;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.VerticalLayout;

/** The plugin's name, version and what it is, beside its icon. */
public final class AboutDialogDescriptor extends DialogDescriptor {
    private static final int ICON_SIZE = 32;

    public AboutDialogDescriptor() {
        super(LocalizeValue.localizeTODO("About Turtle Commander"));
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        AdvancedLabel name = AdvancedLabel.create();
        name.updatePresentation(presentation -> presentation.append("Turtle Commander", TextAttribute.REGULAR_BOLD));

        VerticalLayout info = VerticalLayout.create(Space.SMALL);
        info.add(name);
        info.add(Label.create(LocalizeValue.of("Version: " + pluginVersion())));
        info.add(Label.create(LocalizeValue.localizeTODO("A dual-panel file manager")));

        DockLayout layout = DockLayout.create();
        layout.left(ImageBox.create(ImageEffects.resize(TurtleCommanderIconGroup.turtlecommander(), ICON_SIZE)));
        layout.center(info);
        info.paddingBuilder().leftSet(Space.LARGE).apply();
        return layout;
    }

    /** Resolved from this class's own loader, so the plugin id never has to be repeated here. */
    private static String pluginVersion() {
        PluginDescriptor descriptor = PluginManager.getPlugin(AboutDialogDescriptor.class);
        return descriptor != null ? descriptor.getVersion() : "unknown";
    }

    /** Nothing to confirm - the one button only closes. */
    @Override
    public AnAction[] createActions(boolean inverseOrder) {
        return new AnAction[]{new DialogOkAction(LocalizeValue.localizeTODO("Close"))};
    }
}
