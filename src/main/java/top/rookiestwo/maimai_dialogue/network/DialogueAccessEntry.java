package top.rookiestwo.maimai_dialogue.network;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record DialogueAccessEntry(
        ResourceLocation dialogueId,
        DialogueAccessStatus status
) {
    public DialogueAccessEntry {
        Objects.requireNonNull(dialogueId, "dialogueId");
        Objects.requireNonNull(status, "status");
    }
}
