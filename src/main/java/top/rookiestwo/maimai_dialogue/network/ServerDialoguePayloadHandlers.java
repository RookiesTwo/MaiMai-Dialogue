package top.rookiestwo.maimai_dialogue.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueAccessResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueRequestResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.QueryDialogueAccessC2S;
import top.rookiestwo.maimai_dialogue.network.payload.RequestDialogueC2S;
import top.rookiestwo.maimai_dialogue.server.DialogueAccessService;

public final class ServerDialoguePayloadHandlers {
    private ServerDialoguePayloadHandlers() {
    }

    public static void handleRequestDialogue(
            RequestDialogueC2S payload,
            IPayloadContext context
    ) {
        ServerPlayer player = (ServerPlayer) context.player();
        DialogueAccessService.INSTANCE
                .evaluate(player, payload.dialogueId())
                .thenAccept(status -> context.reply(
                        new DialogueRequestResultS2C(
                                payload.requestId(),
                                payload.dialogueId(),
                                status
                        )
                ));
    }

    public static void handleQueryDialogueAccess(
            QueryDialogueAccessC2S payload,
            IPayloadContext context
    ) {
        ServerPlayer player = (ServerPlayer) context.player();
        DialogueAccessService.INSTANCE
                .evaluateAll(player, payload.dialogueIds())
                .thenAccept(entries -> context.reply(
                        new DialogueAccessResultS2C(
                                payload.requestId(),
                                entries
                        )
                ));
    }
}
