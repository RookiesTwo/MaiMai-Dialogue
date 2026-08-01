package top.rookiestwo.maimai_dialogue.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.client.ClientDialoguePayloadHandlers;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueAccessResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueRequestResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.OpenDialogueS2C;
import top.rookiestwo.maimai_dialogue.network.payload.QueryDialogueAccessC2S;
import top.rookiestwo.maimai_dialogue.network.payload.RequestDialogueC2S;

@EventBusSubscriber(
        modid = MaiMaiDialogue.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD
)
@SuppressWarnings("removal")
public final class DialogueNetwork {
    public static final String PROTOCOL_VERSION = "1";

    private DialogueNetwork() {
    }

    @SubscribeEvent
    // 注册 Dialogue 打开、请求和权限查询使用的全部 payload。
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(
                RequestDialogueC2S.TYPE,
                RequestDialogueC2S.STREAM_CODEC,
                ServerDialoguePayloadHandlers::handleRequestDialogue
        );
        registrar.playToServer(
                QueryDialogueAccessC2S.TYPE,
                QueryDialogueAccessC2S.STREAM_CODEC,
                ServerDialoguePayloadHandlers::handleQueryDialogueAccess
        );
        registrar.playToClient(
                DialogueRequestResultS2C.TYPE,
                DialogueRequestResultS2C.STREAM_CODEC,
                ClientDialoguePayloadHandlers::handleDialogueRequestResult
        );
        registrar.playToClient(
                DialogueAccessResultS2C.TYPE,
                DialogueAccessResultS2C.STREAM_CODEC,
                ClientDialoguePayloadHandlers::handleDialogueAccessResult
        );
        registrar.playToClient(
                OpenDialogueS2C.TYPE,
                OpenDialogueS2C.STREAM_CODEC,
                ClientDialoguePayloadHandlers::handleOpenDialogue
        );
    }
}
