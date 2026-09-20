package top.rookiestwo.maimai_dialogue_editor.material;

import com.google.gson.*;
import top.rookiestwo.maimai_dialogue_editor.project.*;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Asset IDs, validation and standard resource-pack export. Preview reads project assets directly. */
public final class MaterialPack {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    @FunctionalInterface public interface Writer { void write(String relative, byte[] bytes) throws IOException; }
    public record Problem(ResourceKey resource, String field, String message) {}
    public record External(Set<String> images, Set<String> sounds) {
        public static final External EMPTY = new External(Set.of(), Set.of());
        public External { images = Set.copyOf(images); sounds = Set.copyOf(sounds); }
    }
    public static boolean contains(ProjectDraft draft, ResourceKind kind, String id) {
        if (kind == ResourceKind.IMAGE) return draft.resourceKeys().stream().anyMatch(key ->
                key.kind() == kind && imageId(key, draft.namespace()).equals(id));
        if (kind == ResourceKind.SOUND) return draft.resourceKeys().stream().anyMatch(key ->
                key.kind() == kind && (draft.namespace() + ":" + draft.revision(key).summary().name()).equals(id));
        return false;
    }
    public static String imageId(ResourceKey key, String namespace) { return key.id(namespace) + ".png"; }
    public static boolean portable(String path) {
        if (!ResourceKey.validPath(path)) return false;
        for (String segment : path.split("/")) {
            if (segment.endsWith(".") || segment.split("\\.", 2)[0].matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])")) return false;
        }
        return true;
    }
    public static String string(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }
    public static List<Problem> validate(ProjectDraft draft) {
        List<Problem> result = new ArrayList<>();
        for (ResourceKey key : draft.resourceKeys()) {
            if (!key.kind().material()) continue;
            try {
                draft.load(key);
                if (!portable(key.path())) { result.add(new Problem(key, "$id", "Invalid material path")); continue; }
                JsonElement value = draft.resource(key);
                if (!(value instanceof JsonObject data)) { result.add(new Problem(key, "$", "Invalid material")); continue; }
                String id = string(data.get("blob"));
                ProjectBlob blob = draft.blob(id);
                if (blob == null || !id.endsWith(key.kind() == ResourceKind.IMAGE ? ".png" : ".ogg")) {
                    result.add(new Problem(key, "blob", "Missing material file")); continue;
                }
                blob.read();
                if (key.kind() == ResourceKind.SOUND) {
                    if (!portable(string(data.get("event")))) result.add(new Problem(key, "event", "Invalid sound event"));
                    if (data.has("stream") && (!data.get("stream").isJsonPrimitive()
                            || !data.get("stream").getAsJsonPrimitive().isBoolean()))
                        result.add(new Problem(key, "stream", "Invalid stream flag"));
                }
            } catch (IOException | RuntimeException failure) {
                result.add(new Problem(key, "blob", String.valueOf(failure.getMessage())));
            }
        }
        return List.copyOf(result);
    }
    public static void write(ProjectDraft draft, Writer writer) throws IOException {
        write(draft, writer, draft.namespace());
    }
    public static void write(ProjectDraft draft, Writer writer, String namespace) throws IOException {
        if (!draft.hasValidMetadata() || !portable(draft.namespace())) throw new IOException("Invalid project namespace");
        if (!namespace.matches("[a-z0-9_.-]+") || !portable(namespace)) throw new IOException("Invalid asset namespace");
        List<Problem> errors = validate(draft);
        if (!errors.isEmpty()) throw new IOException(errors.getFirst().resource() + ": " + errors.getFirst().message());
        JsonObject sounds = new JsonObject();
        for (ResourceKey key : draft.resourceKeys()) {
            if (!key.kind().material()) continue;
            JsonObject data = draft.resource(key).getAsJsonObject();
            byte[] bytes = draft.blob(string(data.get("blob"))).read();
            if (key.kind() == ResourceKind.IMAGE) {
                writer.write("assets/" + namespace + "/textures/" + key.path() + ".png", bytes);
            } else {
                writer.write("assets/" + namespace + "/sounds/" + key.path() + ".ogg", bytes);
                String event = string(data.get("event"));
                if (!sounds.has(event)) {
                    JsonObject definition = new JsonObject();
                    definition.addProperty("replace", true);
                    definition.add("sounds", new JsonArray());
                    sounds.add(event, definition);
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("name", key.id(namespace));
                entry.addProperty("stream", data.has("stream") && data.get("stream").getAsBoolean());
                sounds.getAsJsonObject(event).getAsJsonArray("sounds").add(entry);
            }
        }
        if (!sounds.isEmpty()) writer.write("assets/" + namespace + "/sounds.json",
                (JSON.toJson(sounds) + "\n").getBytes(StandardCharsets.UTF_8));
    }
    /** Only assets affect refresh state; changing dialogue prose does not require a resource reload. */
    public static String signature(ProjectDraft draft) {
        if (draft == null) return "";
        StringBuilder value = new StringBuilder(draft.namespace());
        draft.resourceKeys().stream().filter(k -> k.kind().material()).sorted(Comparator.comparing(ResourceKey::kind)
                .thenComparing(ResourceKey::path)).forEach(k -> value.append('|').append(k).append('=')
                .append(draft.revision(k).fingerprint()));
        return value.toString();
    }
}
