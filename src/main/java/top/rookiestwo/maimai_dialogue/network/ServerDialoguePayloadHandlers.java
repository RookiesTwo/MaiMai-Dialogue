package top.rookiestwo.maimai_dialogue.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueAccessResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueRequestResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.QueryDialogueAccessC2S;
import top.rookiestwo.maimai_dialogue.network.payload.RequestDialogueC2S;
import top.rookiestwo.maimai_dialogue.internal.bootstrap.CommonServices;

public final class ServerDialoguePayloadHandlers {
    private ServerDialoguePayloadHandlers() {
    }

    // 再次校验目标 Dialogue，并把结果返回发起请求的客户端。
    public static void handleRequestDialogue(
            RequestDialogueC2S payload,
            IPayloadContext context
    ) {
        ServerPlayer player = (ServerPlayer) context.player();
        CommonServices.get().dialogueAccess()
                .evaluate(player, payload.dialogueId())
                .thenAccept(status -> context.reply(
                        new DialogueRequestResultS2C(
                                payload.requestId(),
                                payload.dialogueId(),
                                status
                        )
                ));
    }

    // 批量计算选项目标的访问状态并返回客户端。
    public static void handleQueryDialogueAccess(
            QueryDialogueAccessC2S payload,
            IPayloadContext context
    ) {
        ServerPlayer player = (ServerPlayer) context.player();
        CommonServices.get().dialogueAccess()
                .evaluateAll(player, payload.dialogueIds())
                .thenAccept(entries -> context.reply(
                        new DialogueAccessResultS2C(
                                payload.requestId(),
                                entries
                        )
                ));
    }
}
