package top.rookiestwo.maimai_dialogue_editor.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectResource;

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
    private final Map<ResourceKey, ProjectResource> entries = new LinkedHashMap<>();
    private final Map<ResourceKey, List<Use>> inbound = new LinkedHashMap<>();

    public ResourceCatalog(ProjectDraft draft) {
        this.draft = draft;
        if (draft == null) return;
        draft.resourceKeys().stream().sorted(java.util.Comparator.comparing(ResourceKey::kind)
                .thenComparing(ResourceKey::path)).forEach(key -> entries.put(key, draft.revision(key)));
        entries.forEach((source, value) -> {
            for (ResourceReferences.Reference ref : value.summary().references()) {
                String prefix = draft.namespace() + ":";
                if (ref.id().startsWith(prefix)) {
                    String path = ref.id().substring(prefix.length());
                    if (ref.kind() == ResourceKind.IMAGE && path.endsWith(".png")) path = path.substring(0, path.length() - 4);
                    if (ref.kind() == ResourceKind.SOUND) {
                        String event = path;
                        entries.forEach((target, sound) -> {
                            if (target.kind() == ResourceKind.SOUND && sound.summary().name().equals(event))
                                inbound.computeIfAbsent(target, key -> new ArrayList<>()).add(new Use(source, ref.field()));
                        });
                    } else {
                        ResourceKey target = new ResourceKey(ref.kind(), path);
                        inbound.computeIfAbsent(target, key -> new ArrayList<>()).add(new Use(source, ref.field()));
                    }
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
        ProjectResource resource = entries.get(key);
        return resource == null ? "" : resource.summary().name();
    }
    public int stepCount(ResourceKey key) {
        ProjectResource resource = entries.get(key);
        return resource == null ? 0 : resource.summary().steps();
    }
    public String stepText(ResourceKey key, int index) {
        ProjectResource resource = entries.get(key);
        return resource == null ? "" : resource.stepText(index);
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
            case VISUAL_ASSET -> JsonParser.parseString("{\"variants\":{},\"sampling\":\"linear\"}").getAsJsonObject();
            case SCENE -> new JsonObject();
            default -> throw new IllegalArgumentException("Resource editor is not available: " + kind);
        };
    }
}
