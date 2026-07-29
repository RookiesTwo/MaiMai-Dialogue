package top.rookiestwo.maimai_dialogue.api.progress;

import net.minecraft.server.level.ServerPlayer;
import top.rookiestwo.maimai_dialogue.progress.ProgressNode;

import java.util.concurrent.CompletionStage;

public interface PlayerProgressService {
    CompletionStage<ProgressSnapshot> snapshot(ServerPlayer player);

    CompletionStage<Boolean> contains(ServerPlayer player, ProgressNode node);

    CompletionStage<ProgressChangeResult> add(
            ServerPlayer player,
            ProgressNode node
    );

    CompletionStage<ProgressChangeResult> remove(
            ServerPlayer player,
            ProgressNode node
    );
}
