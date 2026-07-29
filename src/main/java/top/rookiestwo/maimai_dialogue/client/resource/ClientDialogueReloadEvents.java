package top.rookiestwo.maimai_dialogue.client.resource;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.dialogue.resource.DialogueReloadListener;
import top.rookiestwo.maimai_dialogue.dialogue.resource.DialogueSnapshots;

@EventBusSubscriber(
        modid = MaiMaiDialogue.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
@SuppressWarnings("removal")
public final class ClientDialogueReloadEvents {
    private ClientDialogueReloadEvents() {
    }

    @SubscribeEvent
    public static void registerReloadListeners(
            RegisterClientReloadListenersEvent event
    ) {
        event.registerReloadListener(new DialogueReloadListener(
                DialogueSnapshots::replaceClient
        ));
    }
}
