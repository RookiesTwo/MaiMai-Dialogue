package top.rookiestwo.maimai_dialogue.speaker.resource;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SpeakerSnapshot {
    public static final SpeakerSnapshot EMPTY = new SpeakerSnapshot(Map.of());

    private final Map<ResourceLocation, SpeakerDefinition> definitions;

    public SpeakerSnapshot(
            Map<ResourceLocation, SpeakerDefinition> definitions
    ) {
        Objects.requireNonNull(definitions, "definitions");
        this.definitions = Map.copyOf(definitions);
    }

    public Optional<SpeakerDefinition> find(ResourceLocation speakerId) {
        Objects.requireNonNull(speakerId, "speakerId");
        return Optional.ofNullable(definitions.get(speakerId));
    }

    public int size() {
        return definitions.size();
    }

    public Map<ResourceLocation, SpeakerDefinition> definitions() {
        return definitions;
    }
}
