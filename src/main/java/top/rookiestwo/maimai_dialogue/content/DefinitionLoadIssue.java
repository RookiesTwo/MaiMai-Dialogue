package top.rookiestwo.maimai_dialogue.content;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record DefinitionLoadIssue(
        ResourceLocation resourceId,
        String message
) {
    public DefinitionLoadIssue {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(message, "message");
    }
}
