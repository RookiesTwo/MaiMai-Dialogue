package top.rookiestwo.maimai_dialogue.client.resource;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;
import top.rookiestwo.maimai_dialogue.client.bootstrap.ClientServices;
import net.minecraft.client.Minecraft;

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
    public static void soundEngineReloaded(SoundEngineLoadEvent event) {
        Minecraft.getInstance().execute(() -> ClientServices.get().audio().soundEngineReloaded());
    }

    @SubscribeEvent
    public static void registerReloadListeners(
            RegisterClientReloadListenersEvent event
    ) {
        event.registerReloadListener(new ClientContentReloadListener());
    }
}
