package top.rookiestwo.maimai_dialogue.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import top.rookiestwo.maimai_dialogue.api.DialogueOpenResult;
import top.rookiestwo.maimai_dialogue.api.DialogueService;
import top.rookiestwo.maimai_dialogue.network.DialogueAccessStatus;
import top.rookiestwo.maimai_dialogue.network.payload.OpenDialogueS2C;

import java.util.concurrent.CompletionStage;

public final class DefaultDialogueService implements DialogueService {
    private final DialogueAccessService access;

    public DefaultDialogueService(DialogueAccessService access) {
        this.access = java.util.Objects.requireNonNull(access, "access");
    }

    @Override
    // 完成服务端访问检查后向目标玩家发送打开 payload。
    public CompletionStage<DialogueOpenResult> open(
            ServerPlayer player,
            ResourceLocation dialogueId
    ) {
        return access.evaluate(player, dialogueId)
                .thenApply(status -> {
                    if (status == DialogueAccessStatus.ALLOWED) {
                        PacketDistributor.sendToPlayer(
                                player,
                                new OpenDialogueS2C(dialogueId)
                        );
                        return DialogueOpenResult.SENT;
                    }
                    return switch (status) {
                        case DIALOGUE_NOT_FOUND ->
                                DialogueOpenResult.DIALOGUE_NOT_FOUND;
                        case REQUIREMENTS_NOT_MET ->
                                DialogueOpenResult.REQUIREMENTS_NOT_MET;
                        case PROGRESS_UNAVAILABLE ->
                                DialogueOpenResult.PROGRESS_UNAVAILABLE;
                        case INTERNAL_ERROR ->
                                DialogueOpenResult.INTERNAL_ERROR;
                        case ALLOWED -> throw new IllegalStateException(
                                "Handled above."
                        );
                    };
                });
    }
}
