package top.rookiestwo.maimai_dialogue.speaker.resource;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record SpeakerLoadError(
        ResourceLocation resourceId,
        String message
) {
    public SpeakerLoadError {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(message, "message");
    }
}
