package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Core;
import icyllis.modernui.view.View;
import net.minecraft.client.Minecraft;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import java.util.*;
import java.util.function.*;

/** The same project/external sound picker is used by dialogue audio and action audio. */
final class EditorSoundChoices {
    private EditorSoundChoices() {}
    static void show(ProjectWorkspace project, ChoicePresenter choices, View anchor, String selected,
                     BooleanSupplier accepts, Consumer<String> chosen) {
        if (!accepts.getAsBoolean() || project.draft() == null) return;
        String namespace = project.draft().namespace();
        var ids = new TreeSet<String>(); var catalog = project.resources().catalog();
        catalog.keys().stream().filter(key -> key.kind() == ResourceKind.SOUND).map(catalog::displayName)
                .filter(event -> !event.isBlank()).forEach(event -> ids.add(namespace + ":" + event));
        Minecraft.getInstance().execute(() -> {
            var external = Minecraft.getInstance().getSoundManager().getAvailableSounds().stream()
                    .filter(id -> !id.getNamespace().equals(namespace)).map(Object::toString).sorted().toList();
            Core.getUiHandler().post(() -> {
                if (!accepts.getAsBoolean()) return;
                var items = new ArrayList<ChoicePresenter.Item>();
                ids.forEach(id -> items.add(new ChoicePresenter.Item(id, id)));
                external.forEach(id -> items.add(new ChoicePresenter.Item(id, id)));
                choices.showSearchable(anchor, items, selected, value -> { if (accepts.getAsBoolean()) chosen.accept(value); });
            });
        });
    }
}
