package top.rookiestwo.maimai_dialogue_editor.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Tolerant draft traversal of runtime reference fields; never scans arbitrary text or commands. */
final class ResourceReferences {
    record Reference(ResourceKind kind, String id, String field) {}
    private final List<Reference> result = new ArrayList<>();

    static List<Reference> scan(ResourceKind kind, JsonElement draft) {
        ResourceReferences scanner = new ResourceReferences();
        switch (kind) {
            case DIALOGUE -> {
                scanner.presentation(get(draft, "presentation"), "presentation");
                each(get(draft, "steps"), (value, index) -> scanner.step(value, "steps[" + index + "]"));
                JsonElement end = get(draft, "end");
                scanner.step(end, "end");
                JsonElement exit = get(end, "exit");
                scanner.target(exit, "end.exit");
                if (type(exit, "options")) each(get(exit, "options"), (option, index) ->
                        scanner.target(get(option, "target"), "end.exit.options[" + index + "].target"));
            }
            case PRESENTATION -> scanner.presentation(draft, "");
            case SCENE -> scanner.visualObjects(draft, "");
            default -> { }
        }
        return List.copyOf(scanner.result);
    }

    private void step(JsonElement step, String path) {
        JsonElement speaker = get(step, "speaker");
        if (type(speaker, "set")) add(ResourceKind.SPEAKER, get(speaker, "id"), path + ".speaker.id");
        each(get(step, "actions"), (call, index) -> {
            JsonElement action = get(call, "action");
            if (type(action, "reference")) add(ResourceKind.ACTION, get(action, "id"),
                    path + ".actions[" + index + "].action.id");
        });
    }

    private void target(JsonElement value, String path) {
        if (type(value, "dialogue")) add(ResourceKind.DIALOGUE, get(value, "dialogue"), path + ".dialogue");
    }

    private void presentation(JsonElement value, String path) {
        String prefix = path.isEmpty() ? "" : path + ".";
        if (type(value, "reference")) {
            add(ResourceKind.PRESENTATION, get(value, "id"), prefix + "id");
        } else {
            add(ResourceKind.THEME, get(value, "theme"), prefix + "theme");
            add(ResourceKind.SCENE, get(value, "scene"), prefix + "scene");
            visualObjects(value, path);
        }
    }

    private void visualObjects(JsonElement value, String path) {
        JsonElement objects = get(value, "visual_objects");
        if (objects != null && objects.isJsonObject()) {
            objects.getAsJsonObject().entrySet().forEach(entry -> add(ResourceKind.VISUAL_ASSET,
                    get(entry.getValue(), "asset"), (path.isEmpty() ? "" : path + ".")
                            + "visual_objects[" + entry.getKey() + "].asset"));
        }
    }

    private void add(ResourceKind kind, JsonElement value, String field) {
        String id = string(value);
        // ResourceLocation's default namespace is minecraft, not the owning project namespace.
        if (!id.isEmpty()) result.add(new Reference(kind, id.contains(":") ? id : "minecraft:" + id, field));
    }

    static String string(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    static JsonElement get(JsonElement value, String field) {
        return value instanceof JsonObject object ? object.get(field) : null;
    }

    private static boolean type(JsonElement value, String type) { return type.equals(string(get(value, "type"))); }

    private static void each(JsonElement value, BiConsumer<JsonElement, Integer> consumer) {
        if (value == null || !value.isJsonArray()) return;
        int index = 0;
        for (JsonElement element : value.getAsJsonArray()) consumer.accept(element, index++);
    }
}
