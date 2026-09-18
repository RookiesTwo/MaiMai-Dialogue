package top.rookiestwo.maimai_dialogue_editor.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Snapshot index. The project owns the only editable copy of each resource. */
public final class ResourceCatalog {
    public record Use(ResourceKey source, String field) {}
    private final ProjectDraft draft;
    private final Map<ResourceKey, JsonElement> entries = new LinkedHashMap<>();
    private final Map<ResourceKey, List<Use>> inbound = new LinkedHashMap<>();

    public ResourceCatalog(ProjectDraft draft) {
        this.draft = draft;
        if (draft == null) return;
        JsonObject resources = draft.resources();
        for (ResourceKind kind : ResourceKind.values()) {
            JsonElement group = resources.get(kind.directory());
            if (group == null || !group.isJsonObject()) continue;
            group.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                    entries.put(new ResourceKey(kind, entry.getKey()), entry.getValue()));
        }
        entries.forEach((source, value) -> {
            for (ResourceReferences.Reference ref : ResourceReferences.scan(source.kind(), value)) {
                String prefix = draft.namespace() + ":";
                if (ref.id().startsWith(prefix)) {
                    ResourceKey target = new ResourceKey(ref.kind(), ref.id().substring(prefix.length()));
                    inbound.computeIfAbsent(target, key -> new ArrayList<>()).add(new Use(source, ref.field()));
                }
            }
        });
    }

    public List<ResourceKey> keys() { return List.copyOf(entries.keySet()); }
    public boolean contains(ResourceKey key) { return entries.containsKey(key); }
    public List<Use> users(ResourceKey key) { return List.copyOf(inbound.getOrDefault(key, Collections.emptyList())); }
    public List<Use> deletionBlockers(ResourceKey key) {
        // A self-reference disappears together with its owner.
        return users(key).stream().filter(use -> !use.source().equals(key)).toList();
    }
    public String displayName(ResourceKey key) {
        return key.kind() == ResourceKind.SPEAKER
                ? ResourceReferences.string(ResourceReferences.get(entries.get(key), "name")) : "";
    }
    public List<ResourceKey> search(String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return keys().stream().filter(key -> needle.isEmpty() || key.id(draft.namespace()).toLowerCase(Locale.ROOT).contains(needle)
                || displayName(key).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    public String unusedPath(ResourceKind kind, String seed) {
        String candidate = seed;
        for (int suffix = 2; contains(new ResourceKey(kind, candidate)); suffix++) candidate = seed + "_" + suffix;
        return candidate;
    }

    public static JsonObject emptyDraft(ResourceKind kind) {
        return switch (kind) {
            case DIALOGUE -> JsonParser.parseString("""
                    {"presentation":{"theme":"maimai_dialogue:default"},"steps":[],"end":{"exit":{"type":"return"}}}
                    """).getAsJsonObject();
            case SPEAKER -> JsonParser.parseString("{\"name\":\"\"}").getAsJsonObject();
            default -> throw new IllegalArgumentException("Resource editor is not available: " + kind);
        };
    }
}
