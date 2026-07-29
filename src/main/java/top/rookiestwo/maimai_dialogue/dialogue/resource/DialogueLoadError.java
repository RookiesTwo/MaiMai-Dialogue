package top.rookiestwo.maimai_dialogue.dialogue.resource;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record DialogueLoadError(
        ResourceLocation resourceId,
        String message
) {
    public DialogueLoadError {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(message, "message");
    }
}
