package top.rookiestwo.maimai_dialogue.server.pending;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.internal.bootstrap.CommonServices;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

@EventBusSubscriber(modid = MaiMaiDialogue.MOD_ID)
public final class PendingDialogueEvents {
    private PendingDialogueEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CommonServices.get().pendingDialogues().loadAsync(player)
                .whenComplete((dialogueId, error) -> {
                    if (error != null) {
                        Throwable cause = unwrap(error);
                        if (!(cause instanceof CancellationException)) {
                            MaiMaiDialogue.LOGGER.error(
                                    "Failed to load pending dialogue for player {}",
                                    player.getUUID(),
                                    cause
                            );
                        }
                        return;
                    }
                    dialogueId.ifPresent(id ->
                            CommonServices.get().dialogues()
                                    .restorePending(player, id)
                    );
                });
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(
            PlayerEvent.PlayerLoggedOutEvent event
    ) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CommonServices.get().pendingDialogues().unload(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CommonServices.get().pendingDialogues().awaitOutstandingWrites();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
