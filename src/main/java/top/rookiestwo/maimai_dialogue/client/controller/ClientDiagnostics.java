package top.rookiestwo.maimai_dialogue.client.controller;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;

public final class ClientDiagnostics {
    private ClientDiagnostics() {
    }

    public static void report(String message) {
        MaiMaiDialogue.LOGGER.error(message);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal("[MaiMai Dialogue] " + message),
                    false
            );
        }
    }
}
