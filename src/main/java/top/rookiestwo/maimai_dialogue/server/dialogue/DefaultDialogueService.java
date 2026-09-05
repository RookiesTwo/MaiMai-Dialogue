package top.rookiestwo.maimai_dialogue.server.dialogue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import top.rookiestwo.maimai_dialogue.api.DialogueOpenResult;
import top.rookiestwo.maimai_dialogue.api.DialogueService;
import top.rookiestwo.maimai_dialogue.content.DefinitionRegistry;
import top.rookiestwo.maimai_dialogue.dialogue.DialogueDefinition;
import top.rookiestwo.maimai_dialogue.network.DialogueAccessStatus;
import top.rookiestwo.maimai_dialogue.network.payload.OpenDialogueS2C;
import top.rookiestwo.maimai_dialogue.server.pending.PendingDialogueService;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class DefaultDialogueService implements DialogueService {
    private final DialogueAccessService access;
    private final Supplier<DefinitionRegistry<DialogueDefinition>> dialogues;
    private final PendingDialogueService pendingDialogues;

    public DefaultDialogueService(
            DialogueAccessService access,
            Supplier<DefinitionRegistry<DialogueDefinition>> dialogues,
            PendingDialogueService pendingDialogues
    ) {
        this.access = java.util.Objects.requireNonNull(access, "access");
        this.dialogues = Objects.requireNonNull(dialogues, "dialogues");
        this.pendingDialogues = Objects.requireNonNull(
                pendingDialogues,
                "pendingDialogues"
        );
    }

    @Override
    // 完成服务端访问检查后向目标玩家发送打开 payload。
    public CompletionStage<DialogueOpenResult> open(
            ServerPlayer player,
            ResourceLocation dialogueId
    ) {
        requireServerThread(player);
        return access.evaluate(player, dialogueId)
                .thenCompose(status -> {
                    if (status != DialogueAccessStatus.ALLOWED) {
                        return java.util.concurrent.CompletableFuture
                                .completedFuture(mapAccessResult(status));
                    }

                    DialogueDefinition definition = dialogues.get()
                            .find(dialogueId)
                            .orElse(null);
                    if (definition == null) {
                        return java.util.concurrent.CompletableFuture
                                .completedFuture(
                                        DialogueOpenResult.DIALOGUE_NOT_FOUND
                                );
                    }
                    if (!definition.mustComplete()) {
                        PacketDistributor.sendToPlayer(
                                player,
                                new OpenDialogueS2C(dialogueId)
                        );
                        return java.util.concurrent.CompletableFuture
                                .completedFuture(DialogueOpenResult.SENT);
                    }

                    return pendingDialogues.prepareOpen(player, dialogueId)
                            .thenApply(preparation -> switch (
                                    preparation.status()
                            ) {
                                case READY -> {
                                    PacketDistributor.sendToPlayer(
                                            player,
                                            OpenDialogueS2C.mustComplete(
                                                    dialogueId,
                                                    preparation
                                                            .completionToken()
                                                            .orElseThrow()
                                            )
                                    );
                                    yield DialogueOpenResult.SENT;
                                }
                                case CONFLICT ->
                                        DialogueOpenResult
                                                .PENDING_DIALOGUE_CONFLICT;
                                case PERSISTENCE_FAILED ->
                                        DialogueOpenResult.PERSISTENCE_FAILED;
                                case PLAYER_OFFLINE ->
                                        DialogueOpenResult.INTERNAL_ERROR;
                            });
                });
    }

    public void restorePending(
            ServerPlayer player,
            ResourceLocation dialogueId
    ) {
        requireServerThread(player);
        if (dialogues.get().find(dialogueId).isEmpty()) {
            top.rookiestwo.maimai_dialogue.MaiMaiDialogue.LOGGER.error(
                    "Cannot restore required dialogue {} for player {} because the definition is missing",
                    dialogueId,
                    player.getUUID()
            );
            return;
        }
        pendingDialogues.activateRestored(player, dialogueId)
                .ifPresent(token -> PacketDistributor.sendToPlayer(
                        player,
                        OpenDialogueS2C.mustComplete(dialogueId, token)
                ));
    }

    public CompletionStage<PendingDialogueService.CompletionResult> complete(
            ServerPlayer player,
            ResourceLocation dialogueId,
            UUID completionToken
    ) {
        requireServerThread(player);
        return pendingDialogues.complete(
                player,
                dialogueId,
                completionToken
        );
    }

    private static DialogueOpenResult mapAccessResult(
            DialogueAccessStatus status
    ) {
        return switch (status) {
            case DIALOGUE_NOT_FOUND -> DialogueOpenResult.DIALOGUE_NOT_FOUND;
            case REQUIREMENTS_NOT_MET, SERVER_TRIGGER_ONLY ->
                    DialogueOpenResult.REQUIREMENTS_NOT_MET;
            case PROGRESS_UNAVAILABLE ->
                    DialogueOpenResult.PROGRESS_UNAVAILABLE;
            case INTERNAL_ERROR -> DialogueOpenResult.INTERNAL_ERROR;
            case ALLOWED -> throw new IllegalStateException("Handled above.");
        };
    }

    private static void requireServerThread(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        var server = Objects.requireNonNull(
                player.getServer(),
                "player server"
        );
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "Dialogue API must be called on the server thread."
            );
        }
    }
}
