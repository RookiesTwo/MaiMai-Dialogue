package top.rookiestwo.maimai_dialogue.server.progress;

import top.rookiestwo.maimai_dialogue.progress.ProgressDataException;
import top.rookiestwo.maimai_dialogue.progress.ProgressNode;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.api.progress.PlayerProgressService;
import top.rookiestwo.maimai_dialogue.api.progress.ProgressChangeResult;
import top.rookiestwo.maimai_dialogue.api.progress.ProgressSnapshot;
import top.rookiestwo.maimai_dialogue.server.progress.storage.NbtProgressStore;
import top.rookiestwo.maimai_dialogue.server.progress.storage.ProgressStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class DefaultPlayerProgressService implements PlayerProgressService {
    private final ProgressStore store;
    private final Map<UUID, PlayerProgressState> onlinePlayers = new HashMap<>();
    private final Map<UUID, CompletableFuture<ProgressSnapshot>> loadingPlayers =
            new HashMap<>();
    private final Map<UUID, Throwable> loadFailures = new HashMap<>();

    public DefaultPlayerProgressService() {
        this(new NbtProgressStore());
    }

    public DefaultPlayerProgressService(ProgressStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    // 在 IO 线程加载玩家进度，并回到 server thread 发布在线状态。
    public CompletionStage<ProgressSnapshot> load(ServerPlayer player) {
        MinecraftServer server = requireServerThread(player);
        UUID playerId = player.getUUID();

        PlayerProgressState loaded = onlinePlayers.get(playerId);
        if (loaded != null) {
            return CompletableFuture.completedFuture(loaded.snapshot());
        }

        CompletableFuture<ProgressSnapshot> existing = loadingPlayers.get(playerId);
        if (existing != null) {
            return existing;
        }

        loadFailures.remove(playerId);
        CompletableFuture<ProgressSnapshot> result = new CompletableFuture<>();
        loadingPlayers.put(playerId, result);

        CompletableFuture
                .supplyAsync(
                        () -> new PlayerProgressState(
                                playerId,
                                store.load(server, playerId)
                        ),
                        Util.ioPool()
                )
                .whenComplete((state, error) -> server.execute(() -> {
                    if (loadingPlayers.get(playerId) != result) {
                        return;
                    }
                    loadingPlayers.remove(playerId);
                    if (error != null) {
                        Throwable cause = unwrap(error);
                        loadFailures.put(playerId, cause);
                        result.completeExceptionally(cause);
                        return;
                    }
                    onlinePlayers.put(playerId, state);
                    result.complete(state.snapshot());
                }));

        return result;
    }

    // 玩家离线时移除缓存，并补写尚未持久化的 revision。
    public void unload(ServerPlayer player) {
        MinecraftServer server = requireServerThread(player);
        UUID playerId = player.getUUID();
        PlayerProgressState state = onlinePlayers.remove(playerId);
        CompletableFuture<ProgressSnapshot> loading =
                loadingPlayers.remove(playerId);
        if (loading != null) {
            loading.cancel(false);
        }
        loadFailures.remove(playerId);

        if (state != null && state.persistedRevision < state.revision) {
            long revision = state.revision;
            CompletableFuture<Void> retry = enqueueSave(
                    server,
                    state,
                    revision,
                    Set.copyOf(state.nodes)
            );
            retry.whenComplete((ignored, error) -> {
                if (error != null) {
                    MaiMaiDialogue.LOGGER.error(
                            "Failed to flush progress for player {} on logout",
                            playerId,
                            unwrap(error)
                    );
                }
            });
        }
    }

    public Optional<ProgressSnapshot> snapshotNow(ServerPlayer player) {
        requireServerThread(player);
        PlayerProgressState state = onlinePlayers.get(player.getUUID());
        return state == null ? Optional.empty() : Optional.of(state.snapshot());
    }

    @Override
    public CompletionStage<ProgressSnapshot> snapshot(ServerPlayer player) {
        requireServerThread(player);
        return requireLoaded(player).thenApply(PlayerProgressState::snapshot);
    }

    @Override
    public CompletionStage<Boolean> contains(
            ServerPlayer player,
            ProgressNode node
    ) {
        Objects.requireNonNull(node, "node");
        return snapshot(player).thenApply(snapshot -> snapshot.contains(node));
    }

    @Override
    // 添加节点并等待对应 revision 完成持久化。
    public CompletionStage<ProgressChangeResult> add(
            ServerPlayer player,
            ProgressNode node
    ) {
        Objects.requireNonNull(node, "node");
        MinecraftServer server = requireServerThread(player);
        PlayerProgressState state = requireLoadedNow(player);
        if (!state.nodes.add(node)) {
            return CompletableFuture.completedFuture(
                    ProgressChangeResult.ALREADY_PRESENT
            );
        }
        return persistMutation(
                server,
                state,
                ProgressChangeResult.ADDED
        );
    }

    @Override
    // 删除节点并等待对应 revision 完成持久化。
    public CompletionStage<ProgressChangeResult> remove(
            ServerPlayer player,
            ProgressNode node
    ) {
        Objects.requireNonNull(node, "node");
        MinecraftServer server = requireServerThread(player);
        PlayerProgressState state = requireLoadedNow(player);
        if (!state.nodes.remove(node)) {
            return CompletableFuture.completedFuture(
                    ProgressChangeResult.NOT_PRESENT
            );
        }
        return persistMutation(
                server,
                state,
                ProgressChangeResult.REMOVED
        );
    }

    private CompletionStage<PlayerProgressState> requireLoaded(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerProgressState state = onlinePlayers.get(playerId);
        if (state != null) {
            return CompletableFuture.completedFuture(state);
        }
        Throwable failure = loadFailures.get(playerId);
        if (failure != null) {
            return CompletableFuture.failedFuture(failure);
        }
        return load(player).thenApply(ignored -> requireLoadedNow(player));
    }

    private PlayerProgressState requireLoadedNow(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerProgressState state = onlinePlayers.get(playerId);
        if (state != null) {
            return state;
        }
        Throwable failure = loadFailures.get(playerId);
        if (failure != null) {
            throw new ProgressDataException(
                    "Progress data is unavailable for player " + playerId,
                    failure
            );
        }
        throw new ProgressDataException(
                "Progress data is still loading for player " + playerId
        );
    }

    private CompletionStage<ProgressChangeResult> persistMutation(
            MinecraftServer server,
            PlayerProgressState state,
            ProgressChangeResult result
    ) {
        long revision = ++state.revision;
        CompletableFuture<Void> save = enqueueSave(
                server,
                state,
                revision,
                Set.copyOf(state.nodes)
        );
        return save.thenApply(ignored -> result);
    }

    // 把本次快照串接到保存队列，前一次失败也不会阻断后续保存。
    private CompletableFuture<Void> enqueueSave(
            MinecraftServer server,
            PlayerProgressState state,
            long revision,
            Set<ProgressNode> nodes
    ) {
        CompletableFuture<Void> save = state.saveTail
                .handle((ignored, previousError) -> null)
                .thenRunAsync(
                        () -> store.save(server, state.playerId, nodes),
                        Util.ioPool()
                );
        state.saveTail = save;

        save.whenComplete((ignored, error) -> server.execute(() -> {
            if (error == null) {
                state.persistedRevision = Math.max(
                        state.persistedRevision,
                        revision
                );
            }
        }));
        return save;
    }

    private static MinecraftServer requireServerThread(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        MinecraftServer server = Objects.requireNonNull(
                player.getServer(),
                "player server"
        );
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "Player progress API must be called on the server thread."
            );
        }
        return server;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null
                && current instanceof java.util.concurrent.CompletionException) {
            current = current.getCause();
        }
        return current;
    }

}
