package top.rookiestwo.maimai_dialogue.theme.resource;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ThemeLoadError(
        ResourceLocation resourceId,
        String message
) {
    public ThemeLoadError {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(message, "message");
    }
}
