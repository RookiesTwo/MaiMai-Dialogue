package top.rookiestwo.maimai_dialogue.network.client;

import top.rookiestwo.maimai_dialogue.client.bootstrap.ClientServices;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueAccessResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.DialogueRequestResultS2C;
import top.rookiestwo.maimai_dialogue.network.payload.OpenDialogueS2C;
import top.rookiestwo.maimai_dialogue.network.payload.OptionCommandResultS2C;

public final class ClientDialoguePayloadHandlers {
    private ClientDialoguePayloadHandlers() {
    }

    public static void handleOpenDialogue(
            OpenDialogueS2C payload,
            IPayloadContext context
    ) {
        ClientServices.get().dialogues().handleOpen(payload);
    }

    public static void handleDialogueRequestResult(
            DialogueRequestResultS2C payload,
            IPayloadContext context
    ) {
        ClientServices.get().dialogues().handleRequestResult(payload);
    }

    public static void handleDialogueAccessResult(
            DialogueAccessResultS2C payload,
            IPayloadContext context
    ) {
        ClientServices.get().dialogues().handleAccessResult(payload);
    }

    public static void handleOptionCommandResult(
            OptionCommandResultS2C payload,
            IPayloadContext context
    ) {
        ClientServices.get().dialogues().handleOptionCommandResult(payload);
    }
}
