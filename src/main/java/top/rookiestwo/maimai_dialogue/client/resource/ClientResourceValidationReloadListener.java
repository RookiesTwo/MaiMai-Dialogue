package top.rookiestwo.maimai_dialogue.client.resource;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.dialogue.resource.DialogueSnapshots;
import top.rookiestwo.maimai_dialogue.presentation.action.resource.ActionSnapshots;
import top.rookiestwo.maimai_dialogue.speaker.resource.SpeakerSnapshots;
import top.rookiestwo.maimai_dialogue.theme.resource.ThemeSnapshots;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class ClientResourceValidationReloadListener
        implements PreparableReloadListener {
    @Override
    public CompletableFuture<Void> reload(
            PreparationBarrier barrier,
            ResourceManager resourceManager,
            ProfilerFiller preparationProfiler,
            ProfilerFiller reloadProfiler,
            Executor backgroundExecutor,
            Executor gameExecutor
    ) {
        return barrier.wait(List.<String>of())
                .thenAcceptAsync(ignored -> {
                    List<String> errors = ClientResourceValidator.validate(
                            resourceManager,
                            DialogueSnapshots.client(),
                            SpeakerSnapshots.client(),
                            ThemeSnapshots.client(),
                            ActionSnapshots.client()
                    );
                    errors.forEach(error -> MaiMaiDialogue.LOGGER.error(
                            "Dialogue resource validation: {}",
                            error
                    ));
                    if (errors.isEmpty()) {
                        MaiMaiDialogue.LOGGER.info(
                                "Validated all client Dialogue resource "
                                        + "references successfully."
                        );
                    } else {
                        MaiMaiDialogue.LOGGER.error(
                                "Client Dialogue resource validation found "
                                        + "{} errors.",
                                errors.size()
                        );
                    }
                }, gameExecutor);
    }
}
