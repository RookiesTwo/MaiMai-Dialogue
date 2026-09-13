package top.rookiestwo.maimai_dialogue.client.audio;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.client.bootstrap.ClientServices;

@EventBusSubscriber(modid = MaiMaiDialogue.MOD_ID, value = Dist.CLIENT)
public final class ClientAudioEvents {
    private ClientAudioEvents() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) { ClientServices.get().audio().tick(); }

    @SubscribeEvent
    public static void selectMusic(SelectMusicEvent event) {
        if (ClientServices.get().audio().suppressVanillaMusic()) event.overrideMusic(null);
    }

    @SubscribeEvent
    public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) { ClientServices.get().audio().close(); }

    @SubscribeEvent
    public static void soundStarted(PlaySoundSourceEvent event) { MinecraftAudioBackend.soundStarted(event.getSound()); }

    @SubscribeEvent
    public static void streamStarted(PlayStreamingSourceEvent event) { MinecraftAudioBackend.soundStarted(event.getSound()); }
}
