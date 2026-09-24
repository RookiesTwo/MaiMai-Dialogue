package top.rookiestwo.maimai_dialogue_editor.document;

import com.google.gson.JsonObject;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;

import static top.rookiestwo.maimai_dialogue_editor.document.DialogueDraft.*;

/** Shared by committed edits and disk-only snapshots of a still-focused text field. */
public enum ContentTextField {
    NAME("name", "name"), TEXT("text", "text"), SPEAKER_ID("speaker.id", "id"),
    EXIT_DIALOGUE("exit.dialogue", "dialogue"), OPTION_TEXT("option.text", "text"),
    OPTION_DIALOGUE("option.target.dialogue", "dialogue"), RANDOM_TEXT("random_text", "text"),
    REQUIRES("requires", "requires"), SKIP_SUMMARY("skip_summary", "skip_summary");

    private final String group;
    private final String property;
    ContentTextField(String group, String property) { this.group = group; this.property = property; }
    public String group() { return group; }

    public boolean apply(ResourceKind kind, JsonObject data, ContentWorkspace.Cursor cursor, String value) {
        if (this == RANDOM_TEXT) {
            if (kind != ResourceKind.DIALOGUE) return false;
            var text = get(node(data, cursor.step()), "text");
            if (text == null || !text.isJsonArray() || cursor.variant() < 0 || cursor.variant() >= text.getAsJsonArray().size()) return false;
            text.getAsJsonArray().set(cursor.variant(), new com.google.gson.JsonPrimitive(value)); return true;
        }
        JsonObject target = target(kind, data, cursor);
        if (target == null) return false;
        target.addProperty(property, value);
        return true;
    }

    private JsonObject target(ResourceKind kind, JsonObject data, ContentWorkspace.Cursor cursor) {
        if (this == NAME) return kind == ResourceKind.SPEAKER ? data : null;
        if (kind != ResourceKind.DIALOGUE) return null;
        if (this == REQUIRES || this == SKIP_SUMMARY) return data.has(property) ? data : null;
        JsonObject node = node(data, cursor.step());
        if (node == null) return null;
        if (this == TEXT) return isString(node.get("text")) ? node : null;
        if (this == SPEAKER_ID) {
            JsonObject speaker = object(node.get("speaker"));
            return "set".equals(string(speaker, "type")) ? speaker : null;
        }
        if (cursor.step() != END) return null;
        JsonObject exit = exit(data);
        if (this == EXIT_DIALOGUE) return "dialogue".equals(string(exit, "type")) ? exit : null;
        if (!"options".equals(string(exit, "type"))) return null;
        JsonObject option = option(data, cursor.option());
        if (option == null || this == OPTION_TEXT) return option;
        JsonObject target = object(option.get("target"));
        return "dialogue".equals(string(target, "type")) ? target : null;
    }
}
