package top.rookiestwo.maimai_dialogue_editor.project;

import com.google.gson.*;
import top.rookiestwo.maimai_dialogue_editor.resource.*;
import java.util.*;

/** Compact navigation/reference metadata; no dialogue text or editor cursor state. */
final class ProjectIndex {
    @FunctionalInterface interface LoaderFactory {
        ProjectResource.Loader create(ResourceKey key, String hash);
    }
    static JsonObject encode(ProjectDraft draft) {
        JsonObject index = new JsonObject();
        JsonArray groups = new JsonArray();
        Arrays.stream(ResourceKind.values()).filter(draft.groups()::contains).forEach(kind -> groups.add(kind.directory()));
        index.add("groups", groups);
        index.add("extra_groups", draft.extraGroups());
        JsonArray resources = new JsonArray();
        draft.entries().entrySet().stream().sorted(Comparator
                .comparing((Map.Entry<ResourceKey, ProjectResource> e) -> e.getKey().kind().ordinal())
                .thenComparing(e -> e.getKey().path())).forEach(entry -> {
            JsonObject resource = new JsonObject();
            resource.addProperty("kind", entry.getKey().kind().directory());
            resource.addProperty("path", entry.getKey().path());
            resource.addProperty("hash", entry.getValue().fingerprint());
            resource.add("summary", ProjectJson.JSON.toJsonTree(entry.getValue().summary()));
            resources.add(resource);
        });
        index.add("resources", resources);
        return index;
    }
    static ProjectDraft decode(JsonObject metadata, JsonElement value, LoaderFactory loaders) throws ProjectException {
        try {
            JsonObject index = value.getAsJsonObject();
            Set<ResourceKind> groups = EnumSet.noneOf(ResourceKind.class);
            for (JsonElement group : index.getAsJsonArray("groups")) groups.add(kind(string(group)));
            JsonObject extras = index.getAsJsonObject("extra_groups");
            if (extras == null) throw new IllegalStateException();
            for (ResourceKind group : groups) if (extras.has(group.directory())) throw new IllegalStateException();
            Map<ResourceKey, ProjectResource> entries = new LinkedHashMap<>();
            for (JsonElement element : index.getAsJsonArray("resources")) {
                JsonObject item = element.getAsJsonObject();
                ResourceKey key = new ResourceKey(kind(string(item.get("kind"))), string(item.get("path")));
                String hash = hash(item.get("hash"));
                JsonObject summary = item.getAsJsonObject("summary");
                String name = string(summary.get("name"));
                if (!summary.get("steps").getAsJsonPrimitive().isNumber()) throw new IllegalStateException();
                int steps = summary.get("steps").getAsBigDecimal().intValueExact();
                if (steps < 0) throw new IllegalStateException();
                List<ResourceReferences.Reference> refs = new ArrayList<>();
                for (JsonElement ref : summary.getAsJsonArray("references")) {
                    JsonObject r = ref.getAsJsonObject();
                    refs.add(new ResourceReferences.Reference(ResourceKind.valueOf(string(r.get("kind"))),
                            string(r.get("id")), string(r.get("field"))));
                }
                if (!groups.contains(key.kind()) || entries.put(key, ProjectResource.lazy(hash,
                        new ProjectResource.Summary(name, steps, refs), loaders.create(key, hash))) != null)
                    throw new IllegalStateException();
            }
            return new ProjectDraft(metadata, entries, groups, extras);
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException | ArithmeticException | NoSuchElementException invalid) {
            throw new ProjectException("invalid_format");
        }
    }
    static String hash(JsonElement value) throws ProjectException {
        String hash = string(value);
        if (!hash.matches("[0-9a-f]{64}")) throw new ProjectException("invalid_format");
        return hash;
    }
    private static String string(JsonElement value) throws ProjectException {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new ProjectException("invalid_format");
        return value.getAsString();
    }
    private static ResourceKind kind(String directory) {
        return Arrays.stream(ResourceKind.values()).filter(k -> k.directory().equals(directory)).findFirst().orElseThrow();
    }
}
