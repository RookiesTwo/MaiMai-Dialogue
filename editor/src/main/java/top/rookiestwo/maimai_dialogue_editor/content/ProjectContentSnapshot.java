package top.rookiestwo.maimai_dialogue_editor.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.resource.ClientContentSnapshot;
import top.rookiestwo.maimai_dialogue.client.session.DialogueContentLookup;
import top.rookiestwo.maimai_dialogue.content.DefinitionRegistry;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.presentation.PresentationDefinition;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneAction;
import top.rookiestwo.maimai_dialogue.presentation.scene.SceneDefinition;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualAssetDefinition;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;
import top.rookiestwo.maimai_dialogue_editor.project.ProjectDraft;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKey;
import top.rookiestwo.maimai_dialogue_editor.resource.ResourceKind;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Frozen authoring input, decoded with the runtime codecs only when referenced. */
public final class ProjectContentSnapshot implements DialogueContentLookup {
    private final ProjectDraft draft;
    private final ClientContentSnapshot external;
    private final Map<ResourceKey, Object> decoded = new HashMap<>();

    public ProjectContentSnapshot(ProjectDraft draft, ClientContentSnapshot external) {
        this.draft = Objects.requireNonNull(draft);
        this.external = Objects.requireNonNull(external);
        if (!draft.hasValidMetadata()) throw new IllegalArgumentException("Invalid project name or namespace");
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
    @Override public Optional<PresentationDefinition> presentation(ResourceLocation id) {
        return find(ResourceKind.PRESENTATION, id, PresentationDefinition.CODEC, external.presentations());
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
        if (!id.getNamespace().equals(draft.namespace())) return fallback.find(id);
        ResourceKey key = new ResourceKey(kind, id.getPath());
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
