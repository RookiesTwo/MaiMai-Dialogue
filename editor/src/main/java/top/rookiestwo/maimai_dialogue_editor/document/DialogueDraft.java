package top.rookiestwo.maimai_dialogue_editor.document;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Tolerant access to authoring JSON. No runtime decoding or normalization of untouched fields. */
public final class DialogueDraft {
    public static final int END = -1;
    private DialogueDraft() {}

    public static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
    public static JsonElement get(JsonObject object, String field) { return object == null ? null : object.get(field); }
    public static String string(JsonElement value) {
        return isString(value) ? value.getAsString() : "";
    }
    public static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }
    public static String string(JsonObject object, String field) { return string(get(object, field)); }
    public static JsonArray array(JsonObject object, String field) {
        JsonElement value = get(object, field);
        return value == null ? new JsonArray() : value.isJsonArray() ? value.getAsJsonArray() : null;
    }
    public static JsonObject node(JsonObject dialogue, int index) {
        if (index == END) return object(get(dialogue, "end"));
        JsonArray steps = array(dialogue, "steps");
        return steps != null && index >= 0 && index < steps.size() ? object(steps.get(index)) : null;
    }
    public static JsonObject exit(JsonObject dialogue) { return object(get(node(dialogue, END), "exit")); }
    public static JsonArray options(JsonObject dialogue) { return array(exit(dialogue), "options"); }
    public static JsonObject option(JsonObject dialogue, int index) {
        JsonArray options = options(dialogue);
        return options != null && index >= 0 && index < options.size() ? object(options.get(index)) : null;
    }
    public static JsonObject typed(String type) {
        JsonObject result = new JsonObject();
        result.addProperty("type", type);
        return result;
    }
    public static JsonObject newStep() {
        JsonObject step = new JsonObject();
        step.addProperty("text", "");
        return step;
    }
    public static JsonObject newEnd() {
        JsonObject end = new JsonObject();
        end.add("exit", typed("return"));
        return end;
    }
    public static JsonObject newOption() {
        JsonObject option = new JsonObject();
        option.addProperty("text", "");
        option.add("target", typed("return"));
        return option;
    }
    public static void insert(JsonArray array, int index, JsonElement value) {
        array.asList().add(index, value);
    }
    public static void move(JsonArray array, int from, int to) {
        JsonElement value = array.remove(from);
        insert(array, to, value);
    }
}
