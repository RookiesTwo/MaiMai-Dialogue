package top.rookiestwo.maimai_dialogue.progress;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;

import java.util.concurrent.CancellationException;

@EventBusSubscriber(modid = MaiMaiDialogue.MOD_ID)
public final class ProgressPlayerEvents {
    private ProgressPlayerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProgressServices.repository().load(player).exceptionally(error -> {
                if (!(error instanceof CancellationException)) {
                    MaiMaiDialogue.LOGGER.error(
                            "Failed to load progress for player {}",
                            player.getUUID(),
                            error
                    );
                }
                return null;
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProgressServices.repository().unload(player);
        }
    }
}
