package top.rookiestwo.maimai_dialogue_editor.project;

import com.google.gson.*;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceReferences;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Immutable resource revision. Only its lazily populated cache changes; revisions are shared by history. */
public final class ProjectResource {
    @FunctionalInterface interface Loader { JsonElement load() throws IOException; }
    public record Summary(String name, int steps, List<ResourceReferences.Reference> references) {
        public Summary { references = List.copyOf(references); }
    }
    private final String fingerprint;
    private final Summary summary;
    private final Loader loader;
    private volatile JsonElement value;

    private ProjectResource(String fingerprint, Summary summary, Loader loader, JsonElement value) {
        this.fingerprint = fingerprint; this.summary = summary; this.loader = loader; this.value = value;
    }
    static ProjectResource of(ResourceKind kind, JsonElement value) {
        JsonElement owned = Objects.requireNonNull(value).deepCopy();
        return new ProjectResource(ProjectJson.hash(ProjectJson.bytes(owned)), summarize(kind, owned), null, owned);
    }
    static ProjectResource lazy(String fingerprint, Summary summary, Loader loader) {
        return new ProjectResource(fingerprint, summary, loader, null);
    }
    public String fingerprint() { return fingerprint; }
    public Summary summary() { return summary; }
    public boolean loaded() { return value != null; }
    public synchronized void load() throws IOException {
        if (value == null) value = Objects.requireNonNull(loader.load());
    }
    public JsonElement copy() {
        JsonElement current = value;
        if (current == null) throw new IllegalStateException("Resource has not been loaded by the IO executor");
        return current.deepCopy();
    }
    public String stepText(int index) {
        JsonElement current = value;
        if (!(current instanceof JsonObject object)) return "";
        JsonElement steps = object.get("steps");
        JsonElement node = index < 0 ? object.get("end") : steps != null && steps.isJsonArray()
                && index < steps.getAsJsonArray().size() ? steps.getAsJsonArray().get(index) : null;
        return node instanceof JsonObject data ? string(data.get("text")) : "";
    }
    private static Summary summarize(ResourceKind kind, JsonElement value) {
        JsonObject object = value instanceof JsonObject data ? data : new JsonObject();
        JsonElement steps = object.get("steps");
        return new Summary(kind == ResourceKind.SPEAKER ? string(object.get("name"))
                        : kind == ResourceKind.SOUND ? string(object.get("event")) : "",
                steps != null && steps.isJsonArray() ? steps.getAsJsonArray().size() : 0,
                ResourceReferences.scan(kind, value));
    }
    private static String string(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof ProjectResource resource && fingerprint.equals(resource.fingerprint);
    }
    @Override public int hashCode() { return fingerprint.hashCode(); }
}
