package top.rookiestwo.maimai_dialogue.dialogue.resource;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class DialogueReloadListener implements PreparableReloadListener {
    private final Consumer<DialogueSnapshot> snapshotConsumer;

    public DialogueReloadListener(
            Consumer<DialogueSnapshot> snapshotConsumer
    ) {
        this.snapshotConsumer = Objects.requireNonNull(
                snapshotConsumer,
                "snapshotConsumer"
        );
    }

    @Override
    public CompletableFuture<Void> reload(
            PreparationBarrier barrier,
            ResourceManager resourceManager,
            ProfilerFiller preparationProfiler,
            ProfilerFiller reloadProfiler,
            Executor backgroundExecutor,
            Executor gameExecutor
    ) {
        return CompletableFuture
                .supplyAsync(
                        () -> DialogueResourceLoader.load(resourceManager),
                        backgroundExecutor
                )
                .thenCompose(barrier::wait)
                .thenAcceptAsync(result -> {
                    result.logErrors(MaiMaiDialogue.LOGGER);
                    snapshotConsumer.accept(result.snapshot());
                    MaiMaiDialogue.LOGGER.info(
                            "Loaded {} dialogue definitions with {} errors.",
                            result.snapshot().size(),
                            result.errors().size()
                    );
                }, gameExecutor);
    }
}
