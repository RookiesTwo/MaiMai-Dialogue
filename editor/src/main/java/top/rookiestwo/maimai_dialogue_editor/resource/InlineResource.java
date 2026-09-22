package top.rookiestwo.maimai_dialogue_editor.resource;

import com.google.gson.JsonObject;
import top.rookiestwo.maimai_dialogue_editor.document.DialogueDraft;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;

/** Captures a draft extraction. Creating the definition and replacing its use form one history edit. */
public final class InlineResource {
    private final ProjectDraft source;
    private final ResourceKey owner;
    private final ResourceKind kind;
    private final JsonObject definition, replacement, reference;

    private InlineResource(ProjectDraft source, ResourceKey owner, ResourceKind kind,
                           JsonObject definition, JsonObject replacement, JsonObject reference) {
        this.source = source; this.owner = owner; this.kind = kind;
        this.definition = definition.deepCopy(); this.replacement = replacement; this.reference = reference;
    }
    public ResourceKind kind() { return kind; }
    public ResourceKey owner() { return owner; }
    public boolean current(ProjectDraft draft) { return source == draft; }
    public ProjectDraft apply(ProjectDraft draft, ResourceKey target) {
        if (!current(draft)) throw new IllegalStateException("stale_source");
        if (target.kind() != kind || !ResourceKey.validPath(target.path())) throw new IllegalArgumentException("invalid_path");
        if (draft.resourceKeys().contains(target)) throw new IllegalArgumentException("duplicate");
        reference.addProperty(kind == ResourceKind.ACTION ? "id" : "asset", target.id(draft.namespace()));
        return draft.withResource(target, definition).withResource(owner, replacement);
    }
    public static InlineResource action(ProjectDraft draft, ResourceKey owner, int step, int index) {
        if (owner.kind() != ResourceKind.DIALOGUE) throw new IllegalArgumentException("Expected Dialogue");
        var root = draft.resource(owner).getAsJsonObject().deepCopy();
        var call = DialogueDraft.node(root, step).getAsJsonArray("actions").get(index).getAsJsonObject();
        var spec = call.getAsJsonObject("action");
        if (!"inline".equals(DialogueDraft.string(spec, "type"))) throw new IllegalArgumentException("Expected inline action");
        var definition = spec.getAsJsonObject("action");
        spec.remove("action"); spec.addProperty("type", "reference");
        return new InlineResource(draft, owner, ResourceKind.ACTION, definition, root, spec);
    }
    public static InlineResource visualAsset(ProjectDraft draft, ResourceKey owner, String objectId) {
        if (owner.kind() != ResourceKind.SCENE) throw new IllegalArgumentException("Expected Scene");
        var root = draft.resource(owner).getAsJsonObject().deepCopy();
        var object = root.getAsJsonObject("visual_objects").getAsJsonObject(objectId);
        if (object.has("asset")) throw new IllegalArgumentException("Expected inline VisualAsset");
        var definition = new JsonObject();
        definition.add("variants", object.get("variants").deepCopy());
        if (object.has("sampling")) definition.add("sampling", object.get("sampling").deepCopy());
        object.remove("variants");
        // Placement fields, initial variant and explicit per-object overrides keep their original meaning.
        return new InlineResource(draft, owner, ResourceKind.VISUAL_ASSET, definition, root, object);
    }
}
