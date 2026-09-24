package top.rookiestwo.maimai_dialogue_editor.client;

import icyllis.modernui.core.Core;
import net.minecraft.client.Minecraft;
import top.rookiestwo.maimai_dialogue.client.bootstrap.ClientServices;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.util.*;
import java.util.function.*;

/** Capture project metadata on UI; read external registries on client; publish only to a current owner. */
public final class EditorResourceCandidates {
    public enum Source { PROJECT, PROJECT_AND_EXTERNAL }

    private EditorResourceCandidates() {}

    public static void references(ProjectWorkspace project, ResourceKind kind, Source source,
                                  BooleanSupplier accepts, Consumer<List<ResourceCandidates.Item>> ready) {
        if (!accepts.getAsBoolean() || project.draft() == null) return;
        String namespace = project.draft().namespace();
        var local = ResourceCandidates.project(project.resources().catalog(), namespace, kind, ResourceCandidates.Label.ID);
        if (source == Source.PROJECT) {
            ready.accept(ResourceCandidates.merge(namespace, local, List.of(), ResourceCandidates.Order.ID));
            return;
        }
        readExternal(() -> {
            var snapshot = ClientServices.get().content().current();
            var ids = switch (kind) {
                case SCENE -> snapshot.scenes().ids();
                case THEME -> snapshot.themes().ids();
                case VISUAL_ASSET -> snapshot.visualAssets().ids();
                case ACTION -> snapshot.actions().ids();
                default -> throw new IllegalArgumentException("No external candidate source: " + kind);
            };
            return ids.stream().map(Object::toString).toList();
        }, accepts, external -> ready.accept(ResourceCandidates.merge(namespace, local, external, ResourceCandidates.Order.ID)));
    }

    public static void sounds(ProjectWorkspace project, BooleanSupplier accepts, Consumer<List<ResourceCandidates.Item>> ready) {
        if (!accepts.getAsBoolean() || project.draft() == null) return;
        String namespace = project.draft().namespace();
        var catalog = project.resources().catalog();
        readExternal(() -> Minecraft.getInstance().getSoundManager().getAvailableSounds().stream().map(Object::toString).toList(),
                accepts, external -> ready.accept(ResourceCandidates.sounds(catalog, namespace, external)));
    }

    private static void readExternal(Supplier<List<String>> read, BooleanSupplier accepts, Consumer<List<String>> ready) {
        Minecraft.getInstance().execute(() -> {
            var result = read.get();
            Core.getUiHandler().post(() -> { if (accepts.getAsBoolean()) ready.accept(result); });
        });
    }
}
