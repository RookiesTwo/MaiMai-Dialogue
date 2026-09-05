package top.rookiestwo.maimai_dialogue.server.pending;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.server.pending.storage.PendingDialogueRecord;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class PendingPlayerState {
    final UUID playerId;
    CompletableFuture<Void> saveTail = CompletableFuture.completedFuture(null);
    private Optional<ResourceLocation> pendingDialogue;
    private ActiveDialogue activeDialogue;
    private boolean completionInProgress;

    PendingPlayerState(PendingDialogueRecord record) {
        playerId = record.playerId();
        pendingDialogue = record.dialogueId();
    }

    Optional<ResourceLocation> pendingDialogue() {
        return pendingDialogue;
    }

    void reserve(ResourceLocation dialogue) {
        pendingDialogue = Optional.of(dialogue);
    }

    void cancelReservation() {
        pendingDialogue = Optional.empty();
    }

    UUID activate(ResourceLocation dialogue) {
        UUID token = UUID.randomUUID();
        activeDialogue = new ActiveDialogue(dialogue, token);
        return token;
    }

    Optional<UUID> activateRestored(ResourceLocation dialogue) {
        if (activeDialogue != null || !pendingDialogue.equals(Optional.of(dialogue))) {
            return Optional.empty();
        }
        return Optional.of(activate(dialogue));
    }

    boolean beginCompletion(ResourceLocation dialogue, UUID token) {
        if (activeDialogue == null || completionInProgress
                || !activeDialogue.dialogueId.equals(dialogue)
                || !activeDialogue.completionToken.equals(token)
                || !pendingDialogue.equals(Optional.of(dialogue))) {
            return false;
        }
        // 先消费 active token，拒绝重复回包；写盘失败仍按原流程等待重新登录恢复。
        completionInProgress = true;
        activeDialogue = null;
        return true;
    }

    // 清除失败仍保留待完成记录，沿用重新登录后恢复的原有流程。
    void finishClear(boolean successful) {
        completionInProgress = false;
        if (successful) {
            pendingDialogue = Optional.empty();
        }
    }

    private record ActiveDialogue(ResourceLocation dialogueId, UUID completionToken) {
    }
}
