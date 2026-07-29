package top.rookiestwo.maimai_dialogue.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.dialogue.resource.DialogueSnapshots;
import top.rookiestwo.maimai_dialogue.network.DialogueAccessEntry;
import top.rookiestwo.maimai_dialogue.network.DialogueAccessStatus;
import top.rookiestwo.maimai_dialogue.progress.ProgressDataException;
import top.rookiestwo.maimai_dialogue.progress.ProgressServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class DialogueAccessService {
    public static final DialogueAccessService INSTANCE =
            new DialogueAccessService();

    private DialogueAccessService() {
    }

    public CompletionStage<DialogueAccessStatus> evaluate(
            ServerPlayer player,
            ResourceLocation dialogueId
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(dialogueId, "dialogueId");

        DialogueDefinition definition = DialogueSnapshots.server()
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

        return ProgressServices.repository().snapshot(player)
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

    public CompletionStage<List<DialogueAccessEntry>> evaluateAll(
            ServerPlayer player,
            List<ResourceLocation> dialogueIds
    ) {
        List<CompletableFuture<DialogueAccessEntry>> evaluations =
                new ArrayList<>(dialogueIds.size());
        for (ResourceLocation dialogueId : dialogueIds) {
            evaluations.add(evaluate(player, dialogueId)
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
