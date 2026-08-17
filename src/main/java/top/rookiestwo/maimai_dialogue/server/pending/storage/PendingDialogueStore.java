package top.rookiestwo.maimai_dialogue.server.pending.storage;

import net.minecraft.server.MinecraftServer;

import java.util.Optional;
import java.util.UUID;

public interface PendingDialogueStore {
    Optional<PendingDialogueRecord> load(
            MinecraftServer server,
            UUID playerId
    );

    void save(
            MinecraftServer server,
            PendingDialogueRecord record
    );
}
