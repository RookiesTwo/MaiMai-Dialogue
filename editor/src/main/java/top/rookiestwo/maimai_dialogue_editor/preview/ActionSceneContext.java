package top.rookiestwo.maimai_dialogue_editor.preview;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.resource.ClientContentSnapshot;
import top.rookiestwo.maimai_dialogue.content.resolve.SceneResolver;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneAction;
import top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition;
import top.rookiestwo.maimai_dialogue_editor.content.ProjectContentSnapshot;
import top.rookiestwo.maimai_dialogue_editor.document.ActionFields;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.io.IOException;
import java.util.*;

/** Background-only inspector metadata; invalid actions must not prevent choosing a Scene target. */
public record ActionSceneContext(SceneDefinition scene) {
    public static ActionSceneContext prepare(ProjectDraft draft, String sceneId, ClientContentSnapshot external) {
        var id = ResourceLocation.parse(sceneId);
        var content = new ProjectContentSnapshot(draft, external).prepare(ResourceKind.SCENE, id);
        var resolved = SceneResolver.resolve(id, content::scene, content::theme, content::visualAsset);
        if (!resolved.sceneErrors().isEmpty() || !resolved.visualErrors().isEmpty())
            throw new IllegalArgumentException(String.join("\n", resolved.sceneErrors()) + "\n" + String.join("\n", resolved.visualErrors()));
        return new ActionSceneContext(resolved.scene());
    }
    public List<String> targets() {
        var targets = new ArrayList<String>(); targets.add("dialogue");
        if (scene.background().isPresent()) targets.add("background");
        targets.addAll(scene.visualObjects().keySet().stream().sorted().toList()); return List.copyOf(targets);
    }
    public List<String> variants(String target) {
        if (target.equals("background")) return scene.background().map(value -> value.variants().keySet().stream().sorted().toList()).orElse(List.of());
        var object = scene.visualObjects().get(target); return object == null ? List.of() : object.variants().keySet().stream().sorted().toList();
    }
    public static JsonObject reference(ProjectDraft draft, String reference, ClientContentSnapshot external) throws IOException {
        var id = ResourceLocation.parse(reference);
        if (id.getNamespace().equals(draft.namespace())) {
            var key = new ResourceKey(ResourceKind.ACTION, id.getPath()); draft.load(key);
            if (!(draft.resource(key) instanceof JsonObject definition)) throw new IllegalArgumentException("Missing SceneAction: " + id);
            return definition.deepCopy();
        }
        var definition = external.actions().find(id).orElseThrow(() -> new IllegalArgumentException("Missing SceneAction: " + id));
        return SceneAction.CODEC.encodeStart(JsonOps.INSTANCE, definition).getOrThrow().getAsJsonObject();
    }
    public static String signature(ProjectDraft draft, String sceneId) {
        var result = new StringBuilder(sceneId);
        draft.resourceKeys().stream().filter(key -> key.kind() == ResourceKind.SCENE || key.kind() == ResourceKind.VISUAL_ASSET)
                .sorted(Comparator.comparing(ResourceKey::toString))
                .forEach(key -> result.append('|').append(key).append('=').append(draft.revision(key).fingerprint()));
        return result.toString();
    }
}
