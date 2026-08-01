package top.rookiestwo.maimai_dialogue.server.progress.storage;

import net.minecraft.server.MinecraftServer;
import top.rookiestwo.maimai_dialogue.progress.ProgressNode;

import java.util.Set;
import java.util.UUID;

public interface ProgressStore {
    Set<ProgressNode> load(MinecraftServer server, UUID playerId);

    void save(
            MinecraftServer server,
            UUID playerId,
            Set<ProgressNode> nodes
    );
}
