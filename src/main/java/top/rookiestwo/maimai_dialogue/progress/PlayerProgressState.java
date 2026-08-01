package top.rookiestwo.maimai_dialogue.progress;

import top.rookiestwo.maimai_dialogue.api.progress.ProgressSnapshot;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class PlayerProgressState {
    final UUID playerId;
    final Set<ProgressNode> nodes;
    long revision;
    long persistedRevision;
    CompletableFuture<Void> saveTail = CompletableFuture.completedFuture(null);

    PlayerProgressState(UUID playerId, Set<ProgressNode> nodes) {
        this.playerId = playerId;
        this.nodes = new HashSet<>(nodes);
    }

    ProgressSnapshot snapshot() {
        return new ProgressSnapshot(playerId, nodes);
    }
}
