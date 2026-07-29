package top.rookiestwo.maimai_dialogue.api.progress;

import top.rookiestwo.maimai_dialogue.progress.ProgressNode;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ProgressSnapshot(
        UUID playerId,
        Set<ProgressNode> nodes
) {
    public ProgressSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(nodes, "nodes");
        nodes = Set.copyOf(nodes);
    }

    public boolean contains(ProgressNode node) {
        return nodes.contains(Objects.requireNonNull(node, "node"));
    }
}
