package top.rookiestwo.maimai_dialogue.dialogue.resource;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class DialogueSnapshot {
    public static final DialogueSnapshot EMPTY = new DialogueSnapshot(Map.of());

    private final Map<ResourceLocation, DialogueDefinition> definitions;

    public DialogueSnapshot(Map<ResourceLocation, DialogueDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        this.definitions = Collections.unmodifiableMap(
                new LinkedHashMap<>(definitions)
        );
    }

    public Optional<DialogueDefinition> find(ResourceLocation dialogueId) {
        Objects.requireNonNull(dialogueId, "dialogueId");
        return Optional.ofNullable(definitions.get(dialogueId));
    }

    public boolean contains(ResourceLocation dialogueId) {
        Objects.requireNonNull(dialogueId, "dialogueId");
        return definitions.containsKey(dialogueId);
    }

    public Set<ResourceLocation> ids() {
        return definitions.keySet();
    }

    public Map<ResourceLocation, DialogueDefinition> definitions() {
        return definitions;
    }

    public int size() {
        return definitions.size();
    }

    public boolean isEmpty() {
        return definitions.isEmpty();
    }
}
