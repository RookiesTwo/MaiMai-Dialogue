package top.rookiestwo.maimai_dialogue_editor.project;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/** Immutable metadata/resource map. Unchanged resource bodies are shared across edits and undo snapshots. */
public final class ProjectDraft {
    public static final int FORMAT_VERSION = 2;
    private final JsonObject metadata;
    private final Map<ResourceKey, ProjectResource> entries;
    private final Set<ResourceKind> groups;
    private final JsonObject extraGroups;
    private final int hash;

    ProjectDraft(JsonObject metadata, Map<ResourceKey, ProjectResource> entries,
                 Set<ResourceKind> groups, JsonObject extraGroups) {
        this.metadata = metadata.deepCopy();
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        this.groups = Set.copyOf(groups);
        this.extraGroups = extraGroups.deepCopy();
        // Gson's parsed numeric primitives can have a different hash from equal constructed numbers.
        // Metadata equality is still checked below; use stable fields for this inexpensive precheck.
        hash = Objects.hash(name(), namespace(), this.entries, this.groups);
    }
    public static ProjectDraft create(String name, String namespace) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("format_version", FORMAT_VERSION);
        metadata.addProperty("name", Objects.requireNonNull(name));
        metadata.addProperty("namespace", Objects.requireNonNull(namespace));
        return new ProjectDraft(metadata, Map.of(), Set.of(), new JsonObject());
    }
    /** Aggregate JSON is an explicit data interchange/test operation, not the on-disk manifest format. */
    public static ProjectDraft fromJson(JsonElement value) throws ProjectException {
        if (!(value instanceof JsonObject object)) throw new ProjectException("invalid_format");
        validateMetadata(object);
        if (!object.has("resources") || !object.get("resources").isJsonObject()) throw new ProjectException("invalid_format");
        JsonObject metadata = object.deepCopy();
        JsonObject resources = metadata.remove("resources").getAsJsonObject();
        var entries = new LinkedHashMap<ResourceKey, ProjectResource>();
        var groups = EnumSet.noneOf(ResourceKind.class);
        var extra = new JsonObject();
        resources.entrySet().forEach(group -> {
            ResourceKind kind = Arrays.stream(ResourceKind.values()).filter(k -> k.directory().equals(group.getKey())).findFirst().orElse(null);
            if (kind == null || !group.getValue().isJsonObject()) extra.add(group.getKey(), group.getValue());
            else {
                groups.add(kind);
                group.getValue().getAsJsonObject().entrySet().forEach(entry ->
                        entries.put(new ResourceKey(kind, entry.getKey()), ProjectResource.of(kind, entry.getValue())));
            }
        });
        return new ProjectDraft(metadata, entries, groups, extra);
    }
    static void validateMetadata(JsonObject object) throws ProjectException {
        JsonElement version = object.get("format_version");
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber())
            throw new ProjectException("invalid_format");
        try {
            if (version.getAsBigDecimal().intValueExact() != FORMAT_VERSION) throw new ProjectException("unsupported_version");
        } catch (ArithmeticException | NumberFormatException invalid) { throw new ProjectException("unsupported_version"); }
        if (!isString(object.get("name")) || !isString(object.get("namespace"))) throw new ProjectException("invalid_format");
    }
    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }
    public String name() { return metadata.get("name").getAsString(); }
    public String namespace() { return metadata.get("namespace").getAsString(); }
    public JsonObject metadata() { return metadata.deepCopy(); }
    Map<ResourceKey, ProjectResource> entries() { return entries; }
    Set<ResourceKind> groups() { return groups; }
    JsonObject extraGroups() { return extraGroups.deepCopy(); }
    public Set<ResourceKey> resourceKeys() { return entries.keySet(); }
    public ProjectResource revision(ResourceKey key) { return entries.get(key); }
    public boolean isLoaded(ResourceKey key) { return !entries.containsKey(key) || entries.get(key).loaded(); }
    public void load(ResourceKey key) throws IOException {
        ProjectResource resource = entries.get(key);
        if (resource != null) resource.load();
    }
    /** Full materialization is reserved for background validation/export and explicit data inspection. */
    public void loadAll() throws IOException { for (ProjectResource resource : entries.values()) resource.load(); }
    public ProjectDraft withName(String name) { return withMetadata("name", name); }
    public ProjectDraft withNamespace(String namespace) { return withMetadata("namespace", namespace); }
    private ProjectDraft withMetadata(String key, String value) {
        if (metadata.get(key).getAsString().equals(value)) return this;
        JsonObject next = metadata();
        next.addProperty(key, Objects.requireNonNull(value));
        return new ProjectDraft(next, entries, groups, extraGroups);
    }
    public boolean hasValidMetadata() { return !name().isBlank() && namespace().matches("[a-z0-9_.-]+"); }
    public boolean hasResourceGroup(ResourceKind kind) { return !extraGroups.has(kind.directory()); }
    /** Missing is null; explicit JSON null remains a JsonNull draft. Never performs disk IO. */
    public JsonElement resource(ResourceKey key) {
        ProjectResource resource = entries.get(key);
        return resource == null ? null : resource.copy();
    }
    public ProjectDraft withResource(ResourceKey key, JsonElement value) {
        if (!hasResourceGroup(key.kind())) throw new IllegalStateException("Invalid resource group");
        ProjectResource next = ProjectResource.of(key.kind(), value);
        if (next.equals(entries.get(key))) return this;
        var resources = new LinkedHashMap<>(entries);
        resources.put(key, next);
        var categories = EnumSet.noneOf(ResourceKind.class);
        categories.addAll(groups);
        categories.add(key.kind());
        return new ProjectDraft(metadata, resources, categories, extraGroups);
    }
    public ProjectDraft withoutResource(ResourceKey key) {
        if (!hasResourceGroup(key.kind())) throw new IllegalStateException("Invalid resource group");
        if (!entries.containsKey(key)) return this;
        var resources = new LinkedHashMap<>(entries);
        resources.remove(key);
        return new ProjectDraft(metadata, resources, groups, extraGroups);
    }
    public JsonObject resources() {
        try { loadAll(); } catch (IOException failure) { throw new UncheckedIOException(failure); }
        JsonObject result = extraGroups();
        for (ResourceKind kind : groups) result.add(kind.directory(), new JsonObject());
        entries.forEach((key, resource) -> result.getAsJsonObject(key.kind().directory()).add(key.path(), resource.copy()));
        return result;
    }
    public JsonObject toJson() {
        JsonObject result = metadata();
        result.add("resources", resources());
        return result;
    }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof ProjectDraft draft && hash == draft.hash && metadata.equals(draft.metadata)
                && entries.equals(draft.entries) && groups.equals(draft.groups) && extraGroups.equals(draft.extraGroups);
    }
    @Override public int hashCode() { return hash; }
}
