package top.rookiestwo.maimai_dialogue.client.resource;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.client.ClientServices;
import top.rookiestwo.maimai_dialogue.content.DefinitionLoadResult;
import top.rookiestwo.maimai_dialogue.content.DefinitionTypes;
import top.rookiestwo.maimai_dialogue.content.JsonDefinitionLoader;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.PresentationDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.SceneDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.VisualAssetDefinition;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneAction;
import top.rookiestwo.maimai_dialogue.speaker.SpeakerDefinition;
import top.rookiestwo.maimai_dialogue.theme.ThemeDefinition;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class ClientContentReloadListener
        implements PreparableReloadListener {
    @Override
    // 加载并验证全部客户端 definition，最后一次性替换当前 snapshot。
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
                        () -> loadAll(resourceManager),
                        backgroundExecutor
                )
                .thenCompose(barrier::wait)
                .thenAcceptAsync(loaded -> {
                    loaded.logIssues();
                    ClientContentSnapshot snapshot = loaded.snapshot();
                    List<String> validationErrors =
                            ClientResourceValidator.validate(
                                    resourceManager,
                                    snapshot
                            );
                    validationErrors.forEach(error ->
                            MaiMaiDialogue.LOGGER.error(
                                    "Dialogue resource validation: {}",
                                    error
                            )
                    );
                    ClientServices.get().content().replace(snapshot);
                    logSummary(snapshot, loaded.issueCount(), validationErrors);
                }, gameExecutor);
    }

    private static LoadedClientContent loadAll(ResourceManager manager) {
        return new LoadedClientContent(
                JsonDefinitionLoader.load(manager, DefinitionTypes.DIALOGUE),
                JsonDefinitionLoader.load(manager, DefinitionTypes.SPEAKER),
                JsonDefinitionLoader.load(manager, DefinitionTypes.THEME),
                JsonDefinitionLoader.load(
                        manager,
                        DefinitionTypes.PRESENTATION
                ),
                JsonDefinitionLoader.load(manager, DefinitionTypes.SCENE),
                JsonDefinitionLoader.load(
                        manager,
                        DefinitionTypes.VISUAL_ASSET
                ),
                JsonDefinitionLoader.load(manager, DefinitionTypes.ACTION)
        );
    }

    private static void logSummary(
            ClientContentSnapshot snapshot,
            int loadIssueCount,
            List<String> validationErrors
    ) {
        MaiMaiDialogue.LOGGER.info(
                "Loaded client Dialogue content: {} dialogues, {} speakers, "
                        + "{} themes, {} presentations, {} scenes, "
                        + "{} visual assets and {} actions with "
                        + "{} load errors.",
                snapshot.dialogues().size(),
                snapshot.speakers().size(),
                snapshot.themes().size(),
                snapshot.presentations().size(),
                snapshot.scenes().size(),
                snapshot.visualAssets().size(),
                snapshot.actions().size(),
                loadIssueCount
        );
        if (validationErrors.isEmpty()) {
            MaiMaiDialogue.LOGGER.info(
                    "Validated all client Dialogue resource references successfully."
            );
        } else {
            MaiMaiDialogue.LOGGER.error(
                    "Client Dialogue resource validation found {} errors.",
                    validationErrors.size()
            );
        }
    }

    private record LoadedClientContent(
            DefinitionLoadResult<DialogueDefinition> dialogues,
            DefinitionLoadResult<SpeakerDefinition> speakers,
            DefinitionLoadResult<ThemeDefinition> themes,
            DefinitionLoadResult<PresentationDefinition> presentations,
            DefinitionLoadResult<SceneDefinition> scenes,
            DefinitionLoadResult<VisualAssetDefinition> visualAssets,
            DefinitionLoadResult<SceneAction> actions
    ) {
        private ClientContentSnapshot snapshot() {
            return new ClientContentSnapshot(
                    dialogues.registry(),
                    speakers.registry(),
                    themes.registry(),
                    presentations.registry(),
                    scenes.registry(),
                    visualAssets.registry(),
                    actions.registry()
            );
        }

        private int issueCount() {
            return dialogues.issues().size()
                    + speakers.issues().size()
                    + themes.issues().size()
                    + presentations.issues().size()
                    + scenes.issues().size()
                    + visualAssets.issues().size()
                    + actions.issues().size();
        }

        private void logIssues() {
            dialogues.logIssues(MaiMaiDialogue.LOGGER, DefinitionTypes.DIALOGUE);
            speakers.logIssues(MaiMaiDialogue.LOGGER, DefinitionTypes.SPEAKER);
            themes.logIssues(MaiMaiDialogue.LOGGER, DefinitionTypes.THEME);
            presentations.logIssues(
                    MaiMaiDialogue.LOGGER,
                    DefinitionTypes.PRESENTATION
            );
            scenes.logIssues(MaiMaiDialogue.LOGGER, DefinitionTypes.SCENE);
            visualAssets.logIssues(
                    MaiMaiDialogue.LOGGER,
                    DefinitionTypes.VISUAL_ASSET
            );
            actions.logIssues(MaiMaiDialogue.LOGGER, DefinitionTypes.ACTION);
        }
    }
}
