package top.rookiestwo.maimai_dialogue.client;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueAccessResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueRequestResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.OpenDialogueS2C;

public final class ClientDialoguePayloadHandlers {
    private ClientDialoguePayloadHandlers() {
    }

    public static void handleOpenDialogue(
            OpenDialogueS2C payload,
            IPayloadContext context
    ) {
        ClientDialogueController.INSTANCE.handleOpen(payload);
    }

    public static void handleDialogueRequestResult(
            DialogueRequestResultS2C payload,
            IPayloadContext context
    ) {
        ClientDialogueController.INSTANCE.handleRequestResult(payload);
    }

    public static void handleDialogueAccessResult(
            DialogueAccessResultS2C payload,
            IPayloadContext context
    ) {
        ClientDialogueController.INSTANCE.handleAccessResult(payload);
    }
}
