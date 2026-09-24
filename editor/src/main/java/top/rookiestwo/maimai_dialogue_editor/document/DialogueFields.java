package top.rookiestwo.maimai_dialogue_editor.document;

import com.google.gson.*;
import com.mojang.serialization.*;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueText;
import top.rookiestwo.maimai_dialogue.dialogue.branch.DialogueOption;
import top.rookiestwo.maimai_dialogue.progress.ProgressExpression;

// 属性编辑与导出共用运行时 Codec，错误保留到具体字段。
public final class DialogueFields {
    private DialogueFields() {}
    public static java.util.Map<String, String> errors(JsonElement value) {
        var result = new java.util.LinkedHashMap<String, String>();
        if (!(value instanceof JsonObject root)) return result;
        for (String field : java.util.List.of("requires", "skip_summary", "must_complete")) collect(result, root, field, field);
        var steps = DialogueDraft.array(root, "steps");
        if (steps != null) for (int i = 0; i < steps.size(); i++) {
            var step = DialogueDraft.object(steps.get(i));
            if (step != null) collect(result, step, "typewriter_interval_ms", "steps[" + i + "].typewriter_interval_ms");
        }
        var end = DialogueDraft.node(root, -1);
        if (end != null) collect(result, end, "typewriter_interval_ms", "end.typewriter_interval_ms");
        var options = DialogueDraft.options(root);
        if (options != null) for (int i = 0; i < options.size(); i++) {
            var option = DialogueDraft.object(options.get(i));
            if (option != null) collect(result, option, "command", "end.exit.options[" + i + "].command");
        }
        return result;
    }
    private static void collect(java.util.Map<String, String> errors, JsonObject object, String field, String path) {
        String error = error(field, object.get(field)); if (!error.isEmpty()) errors.put(path, error);
    }
    public static String error(String field, JsonElement value) {
        if (value == null) return "";
        if (field.equals("command")) {
            var option = new JsonObject(); option.addProperty("text", "option");
            option.add("target", DialogueDraft.typed("return")); option.add("command", value);
            return issue(DialogueOption.CODEC, option);
        }
        Codec<?> codec = switch (field) {
            case "requires" -> ProgressExpression.CODEC;
            case "must_complete" -> Codec.BOOL;
            case "text" -> DialogueText.CODEC;
            case "skip_summary" -> Codec.STRING.validate(text -> text.isBlank() ? DataResult.error(() -> "skip_summary must not be blank.") : DataResult.success(text));
            case "typewriter_interval_ms" -> Codec.INT.validate(number -> number < 0 || number > 1000
                    ? DataResult.error(() -> "typewriter_interval_ms must be between 0 and 1000.") : DataResult.success(number));
            default -> throw new IllegalArgumentException(field);
        };
        if (field.equals("typewriter_interval_ms") && (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())) {
            try { value.getAsBigDecimal().intValueExact(); } catch (RuntimeException invalid) { return "typewriter_interval_ms must be an integer."; }
        }
        return issue(codec, value);
    }
    private static String issue(Codec<?> codec, JsonElement value) {
        return codec.parse(JsonOps.INSTANCE, value).error().map(error -> error.message()).orElse("");
    }
}
