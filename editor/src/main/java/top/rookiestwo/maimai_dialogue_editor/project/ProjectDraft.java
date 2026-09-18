package top.rookiestwo.maimai_dialogue_editor.project;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Objects;

/** Immutable authoring data. Runtime validation must not prevent saving an unfinished draft. */
public final class ProjectDraft {
    public static final int FORMAT_VERSION = 1;
    private final JsonObject data;

    private ProjectDraft(JsonObject data) {
        this.data = data.deepCopy();
    }

    public static ProjectDraft create(String name, String namespace) {
        JsonObject data = new JsonObject();
        data.addProperty("format_version", FORMAT_VERSION);
        data.addProperty("name", Objects.requireNonNull(name));
        data.addProperty("namespace", Objects.requireNonNull(namespace));
        data.add("resources", new JsonObject());
        return new ProjectDraft(data);
    }

    public static ProjectDraft fromJson(JsonElement element) throws ProjectException {
        if (element == null || !element.isJsonObject()) {
            throw new ProjectException("invalid_format");
        }
        JsonObject data = element.getAsJsonObject();
        JsonElement version = data.get("format_version");
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
            throw new ProjectException("invalid_format");
        }
        try {
            if (version.getAsBigDecimal().intValueExact() != FORMAT_VERSION) {
                throw new ProjectException("unsupported_version");
            }
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new ProjectException("unsupported_version");
        }
        if (!isString(data.get("name")) || !isString(data.get("namespace"))
                || !data.has("resources") || !data.get("resources").isJsonObject()) {
            throw new ProjectException("invalid_format");
        }
        // Preserve resource drafts and unrecognized fields; never resolve/flatten runtime content here.
        return new ProjectDraft(data);
    }

    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    public String name() {
        return data.get("name").getAsString();
    }

    public String namespace() {
        return data.get("namespace").getAsString();
    }

    public ProjectDraft withName(String name) {
        JsonObject copy = toJson();
        copy.addProperty("name", Objects.requireNonNull(name));
        return new ProjectDraft(copy);
    }

    public ProjectDraft withNamespace(String namespace) {
        JsonObject copy = toJson();
        copy.addProperty("namespace", Objects.requireNonNull(namespace));
        return new ProjectDraft(copy);
    }

    public boolean hasValidMetadata() {
        return !name().isBlank() && namespace().matches("[a-z0-9_.-]+");
    }

    public JsonObject toJson() {
        return data.deepCopy();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProjectDraft draft && data.equals(draft.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }
}
