package top.rookiestwo.maimai_dialogue.server.pending.storage;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PendingDialogueRecord(
        UUID playerId,
        Optional<ResourceLocation> dialogueId
) {
    public PendingDialogueRecord {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(dialogueId, "dialogueId");
    }

    public static PendingDialogueRecord empty(UUID playerId) {
        return new PendingDialogueRecord(playerId, Optional.empty());
    }
}
