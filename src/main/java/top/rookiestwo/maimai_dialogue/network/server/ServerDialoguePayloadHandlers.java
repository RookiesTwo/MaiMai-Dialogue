package top.rookiestwo.maimai_dialogue.network.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueAccessResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueRequestResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.CompleteRequiredDialogueC2S;
import top.rookiestwo.maimai_dialogue.network.payload.ExecuteOptionCommandC2S;
import top.rookiestwo.maimai_dialogue.network.payload.OptionCommandResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.QueryDialogueAccessC2S;
import top.rookiestwo.maimai_dialogue.network.payload.RequestDialogueC2S;
import top.rookiestwo.maimai_dialogue.internal.bootstrap.CommonServices;
import top.rookiestwo.maimai_dialogue.server.pending.PendingDialogueService;

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
                .evaluateClientRequest(player, payload.dialogueId())
                .thenAccept(status -> context.reply(
                        new DialogueRequestResultS2C(
                                payload.requestId(),
                                payload.dialogueId(),
                                status
                        )
                ));
    }

    public static void handleCompleteRequiredDialogue(
            CompleteRequiredDialogueC2S payload,
            IPayloadContext context
    ) {
        ServerPlayer player = (ServerPlayer) context.player();
        CommonServices.get().dialogues().complete(
                player,
                payload.dialogueId(),
                payload.completionToken()
        ).thenAccept(result -> {
            if (result
                    == PendingDialogueService.CompletionResult.REJECTED) {
                MaiMaiDialogue.LOGGER.debug(
                        "Ignored stale required dialogue completion for {} from player {}",
                        payload.dialogueId(),
                        player.getUUID()
                );
            }
        });
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

    // 只执行服务端 Dialogue definition 中对应 Option 的 command。
    public static void handleExecuteOptionCommand(
            ExecuteOptionCommandC2S payload,
            IPayloadContext context
    ) {
        ServerPlayer player = (ServerPlayer) context.player();
        CommonServices.get().optionCommands()
                .execute(
                        player,
                        payload.dialogueId(),
                        payload.optionIndex()
                )
                .thenAccept(status -> context.reply(
                        new OptionCommandResultS2C(
                                payload.requestId(),
                                payload.dialogueId(),
                                payload.optionIndex(),
                                status
                        )
                ));
    }
}
