package top.rookiestwo.maimai_dialogue.dialogue.resource;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;

@EventBusSubscriber(modid = MaiMaiDialogue.MOD_ID)
public final class ServerDialogueReloadEvents {
    private ServerDialogueReloadEvents() {
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ServerDialogueReloadListener());
    }
}
