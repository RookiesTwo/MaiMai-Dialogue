package top.rookiestwo.maimai_dialogue.server.dialogue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.api.progress.PlayerProgressService;
import top.rookiestwo.maimai_dialogue.content.DefinitionRegistry;
import top.rookiestwo.maimai_dialogue.network.DialogueAccessEntry;
import top.rookiestwo.maimai_dialogue.network.DialogueAccessStatus;
import top.rookiestwo.maimai_dialogue.progress.ProgressDataException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class DialogueAccessService {
    private final Supplier<DefinitionRegistry<DialogueDefinition>> dialogues;
    private final PlayerProgressService progress;

    public DialogueAccessService(
            Supplier<DefinitionRegistry<DialogueDefinition>> dialogues,
            PlayerProgressService progress
    ) {
        this.dialogues = Objects.requireNonNull(dialogues, "dialogues");
        this.progress = Objects.requireNonNull(progress, "progress");
    }

    // 根据服务端 Dialogue definition 和玩家进度计算访问状态。
    public CompletionStage<DialogueAccessStatus> evaluate(
            ServerPlayer player,
            ResourceLocation dialogueId
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(dialogueId, "dialogueId");

        DialogueDefinition definition = dialogues.get()
                .find(dialogueId)
                .orElse(null);
        if (definition == null) {
            return CompletableFuture.completedFuture(
                    DialogueAccessStatus.DIALOGUE_NOT_FOUND
            );
        }
        if (definition.requires().isEmpty()) {
            return CompletableFuture.completedFuture(
                    DialogueAccessStatus.ALLOWED
            );
        }

        return progress.snapshot(player)
                .handle((snapshot, error) -> {
                    if (error != null) {
                        Throwable cause = unwrap(error);
                        return cause instanceof ProgressDataException
                                ? DialogueAccessStatus.PROGRESS_UNAVAILABLE
                                : DialogueAccessStatus.INTERNAL_ERROR;
                    }
                    return definition.requires().orElseThrow()
                            .evaluate(snapshot.nodes())
                            ? DialogueAccessStatus.ALLOWED
                            : DialogueAccessStatus.REQUIREMENTS_NOT_MET;
                });
    }

    // 客户端导航不能主动进入必须完成的根 Dialogue。
    public CompletionStage<DialogueAccessStatus> evaluateClientRequest(
            ServerPlayer player,
            ResourceLocation dialogueId
    ) {
        DialogueDefinition definition = dialogues.get()
                .find(dialogueId)
                .orElse(null);
        if (definition != null && definition.mustComplete()) {
            return CompletableFuture.completedFuture(
                    DialogueAccessStatus.SERVER_TRIGGER_ONLY
            );
        }
        return evaluate(player, dialogueId);
    }

    public CompletionStage<List<DialogueAccessEntry>> evaluateAll(
            ServerPlayer player,
            List<ResourceLocation> dialogueIds
    ) {
        List<CompletableFuture<DialogueAccessEntry>> evaluations =
                new ArrayList<>(dialogueIds.size());
        for (ResourceLocation dialogueId : dialogueIds) {
            evaluations.add(evaluateClientRequest(player, dialogueId)
                    .thenApply(status -> new DialogueAccessEntry(
                            dialogueId,
                            status
                    ))
                    .toCompletableFuture());
        }

        return CompletableFuture.allOf(
                evaluations.toArray(CompletableFuture[]::new)
        ).thenApply(ignored -> evaluations.stream()
                .map(CompletableFuture::join)
                .toList());
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof java.util.concurrent.CompletionException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
