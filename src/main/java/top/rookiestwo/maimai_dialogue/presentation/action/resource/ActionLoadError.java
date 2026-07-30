package top.rookiestwo.maimai_dialogue.presentation.action.resource;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ActionLoadError(
        ResourceLocation resourceId,
        String message
) {
    public ActionLoadError {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(message, "message");
    }
}
