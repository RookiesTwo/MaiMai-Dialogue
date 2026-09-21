package top.rookiestwo.maimai_dialogue_editor.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.resource.ClientContentSnapshot;
import top.rookiestwo.maimai_dialogue.client.session.DialogueContentLookup;
import top.rookiestwo.maimai_dialogue.content.DefinitionRegistry;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneAction;
import top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualAssetDefinition;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceReferences;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Frozen authoring input, decoded with the runtime codecs only when referenced. */
public final class ProjectContentSnapshot implements DialogueContentLookup {
    private final ProjectDraft draft;
    private final ClientContentSnapshot external;
    private final Map<ResourceKey, Object> decoded = new HashMap<>();
    private final Map<ResourceKey, RuntimeException> failures = new HashMap<>();

    public ProjectContentSnapshot(ProjectDraft draft, ClientContentSnapshot external) {
        this.draft = Objects.requireNonNull(draft);
        this.external = Objects.requireNonNull(external);
        if (!draft.hasValidMetadata()) throw new IllegalArgumentException("Invalid project name or namespace");
    }

    /** Called on the IO executor. Load/convert only the reachable graph, including external links back into the project. */
    public ProjectContentSnapshot prepare(ResourceLocation root) {
        return prepare(ResourceKind.DIALOGUE, root);
    }

    public ProjectContentSnapshot prepare(ResourceKind rootKind, ResourceLocation root) {
        record Request(ResourceKind kind, ResourceLocation id) {}
        var pending = new ArrayDeque<Request>();
        var visited = new HashSet<Request>();
        pending.add(new Request(rootKind, root));
        while (!pending.isEmpty()) {
            Request request = pending.removeFirst();
            if (!visited.add(request)) continue;
            if (ProjectDefinitions.type(request.kind()) == null) continue;
            List<ResourceReferences.Reference> references;
            if (request.id().getNamespace().equals(draft.namespace())) {
                ResourceKey key = new ResourceKey(request.kind(), request.id().getPath());
                var revision = draft.revision(key);
                if (revision == null) continue;
                references = revision.summary().references();
                try {
                    draft.load(key);
                    lookup(request.kind(), request.id());
                } catch (IOException failure) {
                    failures.put(key, new UncheckedIOException(failure));
                } catch (RuntimeException failure) {
                    // Invalid future branches fail when actually used, not when preparing another valid dialogue.
                    failures.put(key, failure);
                }
            } else {
                Object definition = lookup(request.kind(), request.id()).orElse(null);
                if (definition == null) continue;
                references = ResourceReferences.scan(request.kind(), encode(request.kind(), definition));
            }
            for (var reference : references) {
                ResourceLocation id = ResourceLocation.tryParse(reference.id());
                if (id != null) pending.addLast(new Request(reference.kind(), id));
            }
        }
        return this;
    }

    private Optional<?> lookup(ResourceKind kind, ResourceLocation id) {
        return switch (kind) {
            case DIALOGUE -> dialogue(id);
            case SPEAKER -> speaker(id);
            case THEME -> theme(id);
            case SCENE -> scene(id);
            case VISUAL_ASSET -> visualAsset(id);
            case ACTION -> action(id);
            default -> Optional.empty();
        };
    }
    @SuppressWarnings("unchecked")
    private static JsonElement encode(ResourceKind kind, Object value) {
        return ((Codec<Object>) ProjectDefinitions.type(kind).codec()).encodeStart(JsonOps.INSTANCE, value).getOrThrow();
    }

    @Override public Optional<DialogueDefinition> dialogue(ResourceLocation id) {
        return find(ResourceKind.DIALOGUE, id, DialogueDefinition.CODEC, external.dialogues());
    }
    @Override public Optional<SpeakerDefinition> speaker(ResourceLocation id) {
        return find(ResourceKind.SPEAKER, id, SpeakerDefinition.CODEC, external.speakers());
    }
    @Override public Optional<ThemeDefinition> theme(ResourceLocation id) {
        return find(ResourceKind.THEME, id, ThemeDefinition.CODEC, external.themes());
    }
    @Override public Optional<SceneDefinition> scene(ResourceLocation id) {
        return find(ResourceKind.SCENE, id, SceneDefinition.CODEC, external.scenes());
    }
    @Override public Optional<VisualAssetDefinition> visualAsset(ResourceLocation id) {
        return find(ResourceKind.VISUAL_ASSET, id, VisualAssetDefinition.CODEC, external.visualAssets());
    }
    @Override public Optional<SceneAction> action(ResourceLocation id) {
        return find(ResourceKind.ACTION, id, SceneAction.CODEC, external.actions());
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> find(ResourceKind kind, ResourceLocation id, Codec<T> codec, DefinitionRegistry<T> fallback) {
        if (!id.getNamespace().equals(draft.namespace())) {
            return fallback.find(id);
        }
        ResourceKey key = new ResourceKey(kind, id.getPath());
        if (failures.containsKey(key)) throw failures.get(key);
        Object cached = decoded.get(key);
        if (cached != null) return Optional.of((T) cached);
        if (!draft.hasResourceGroup(kind)) throw new IllegalArgumentException(kind.directory() + ": invalid resource group");
        var json = draft.resource(key);
        // The project's namespace is authoritative; an old installed pack must not fill a missing draft.
        if (json == null) return Optional.empty();
        T value = codec.parse(JsonOps.INSTANCE, json).getOrThrow(error ->
                new IllegalArgumentException(kind.key() + " " + id + ": " + error));
        decoded.put(key, value);
        return Optional.of(value);
    }
}
