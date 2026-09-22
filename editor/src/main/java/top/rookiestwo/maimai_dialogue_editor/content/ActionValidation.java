package top.rookiestwo.maimai_dialogue_editor.content;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.scene.*;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.presentation.action.*;
import top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition;
import top.rookiestwo.maimai_dialogue_editor.document.ActionFields;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import java.util.*;
import java.util.function.Function;

/** Shared authoring checks; playback preparation remains the authority for targets and write conflicts. */
public final class ActionValidation {
    private ActionValidation() {}
    public static Map<String, String> definitions(ResourceKind kind, JsonElement data) {
        var errors = new LinkedHashMap<String, String>();
        if (kind == ResourceKind.ACTION) action(data, "$", errors);
        else if (kind == ResourceKind.DIALOGUE && data instanceof JsonObject dialogue) {
            if (dialogue.get("steps") instanceof JsonArray steps) for (int i = 0; i < steps.size(); i++) calls(steps.get(i), "steps[" + i + "]", errors);
            calls(dialogue.get("end"), "end", errors);
        }
        return errors;
    }
    private static void action(JsonElement data, String path, Map<String, String> errors) {
        ActionFields.errors(data).forEach((field, error) -> errors.put(field.isEmpty() ? path : path.equals("$") ? field : path + "." + field, error));
    }
    private static void calls(JsonElement node, String path, Map<String, String> errors) {
        if (!(node instanceof JsonObject object) || !(object.get("actions") instanceof JsonArray calls)) return;
        for (int i = 0; i < calls.size(); i++) {
            JsonElement call = calls.get(i); String prefix = path + ".actions[" + i + "]";
            try { SceneActionCall.CODEC.parse(JsonOps.INSTANCE, call).error().ifPresent(error -> errors.put(prefix, error.message())); }
            catch (RuntimeException failure) { errors.put(prefix, String.valueOf(failure.getMessage())); }
            if (ActionFields.text(call, "action.type", "").equals("inline")) action(ActionFields.get(call, "action.action"), prefix + ".action.action", errors);
        }
    }
    public static Map<String, String> sequence(DialogueDefinition definition, SceneDefinition scene,
                                               Function<ResourceLocation, Optional<SceneAction>> lookup) {
        var errors = new LinkedHashMap<String, String>();
        var runtime = new SceneRuntime(scene, 0, lookup, 1);
        var steps = new ArrayList<List<SceneActionCall>>(); definition.steps().forEach(step -> steps.add(step.actions())); steps.add(definition.end().actions());
        for (int i = 0; i < steps.size(); i++) {
            String path = i == definition.steps().size() ? "end.actions" : "steps[" + i + "].actions";
            try {
                var calls = i == 0 ? SceneTransitions.withDefaultFadeIn(steps.get(i), lookup) : steps.get(i);
                var result = runtime.prepare(calls);
                if (!result.errors().isEmpty()) errors.put(path, String.join("\n", result.errors()));
            } catch (RuntimeException failure) { errors.put(path, String.valueOf(failure.getMessage())); }
        }
        return errors;
    }
}
