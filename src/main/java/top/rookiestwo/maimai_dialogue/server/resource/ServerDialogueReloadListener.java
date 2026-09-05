package top.rookiestwo.maimai_dialogue.server.resource;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.content.DefinitionTypes;
import top.rookiestwo.maimai_dialogue.content.JsonDefinitionLoader;
import top.rookiestwo.maimai_dialogue.internal.bootstrap.CommonServices;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class ServerDialogueReloadListener
        implements PreparableReloadListener {
    @Override
    // 在后台加载服务端 Dialogue，并在 reload barrier 后发布结果。
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
                        () -> JsonDefinitionLoader.load(
                                resourceManager,
                                DefinitionTypes.DIALOGUE
                        ),
                        backgroundExecutor
                )
                .thenCompose(barrier::wait)
                .thenAcceptAsync(result -> {
                    result.logIssues(
                            MaiMaiDialogue.LOGGER,
                            DefinitionTypes.DIALOGUE
                    );
                    CommonServices.get().serverDialogues()
                            .replace(result.registry());
                    MaiMaiDialogue.LOGGER.info(
                            "Loaded {} server dialogue definitions with {} errors.",
                            result.registry().size(),
                            result.issues().size()
                    );
                }, gameExecutor);
    }
}
