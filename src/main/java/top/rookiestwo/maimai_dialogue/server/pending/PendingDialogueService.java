package top.rookiestwo.maimai_dialogue.server.pending;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.server.pending.storage.NbtPendingDialogueStore;
import top.rookiestwo.maimai_dialogue.server.pending.storage.PendingDialogueRecord;
import top.rookiestwo.maimai_dialogue.server.pending.storage.PendingDialogueStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class PendingDialogueService {
    private final PendingDialogueStore store;
    private final Map<UUID, PlayerState> onlinePlayers = new HashMap<>();
    private final Map<UUID, CompletableFuture<Optional<ResourceLocation>>>
            loadingPlayers = new HashMap<>();
    private final Map<UUID, Throwable> loadFailures = new HashMap<>();
    private final Set<CompletableFuture<Void>> outstandingWrites =
            ConcurrentHashMap.newKeySet();

    public PendingDialogueService() {
        this(new NbtPendingDialogueStore());
    }

    public PendingDialogueService(PendingDialogueStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    // 首次登录会在 IO 线程创建空文件，损坏文件不会被覆盖。
    public CompletionStage<Optional<ResourceLocation>> loadAsync(
            ServerPlayer player
    ) {
        MinecraftServer server = requireServerThread(player);
        UUID playerId = player.getUUID();

        PlayerState loaded = onlinePlayers.get(playerId);
        if (loaded != null) {
            return CompletableFuture.completedFuture(loaded.pendingDialogue);
        }
        CompletableFuture<Optional<ResourceLocation>> existing =
                loadingPlayers.get(playerId);
        if (existing != null) {
            return existing;
        }

        loadFailures.remove(playerId);
        CompletableFuture<Optional<ResourceLocation>> result =
                new CompletableFuture<>();
        loadingPlayers.put(playerId, result);

        CompletableFuture<PendingDialogueRecord> loadTask = CompletableFuture
                .supplyAsync(
                        () -> loadOrCreate(server, playerId),
                        Util.ioPool()
                );
        CompletableFuture<Void> trackedLoad = loadTask.thenAccept(
                ignored -> {
                }
        );
        outstandingWrites.add(trackedLoad);
        trackedLoad.whenComplete((ignored, error) ->
                outstandingWrites.remove(trackedLoad)
        );
        loadTask.whenComplete((record, error) -> server.execute(() -> {
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
                    PlayerState state = new PlayerState(record);
                    onlinePlayers.put(playerId, state);
                    result.complete(state.pendingDialogue);
                }));
        return result;
    }

    public void unload(ServerPlayer player) {
        requireServerThread(player);
        UUID playerId = player.getUUID();
        onlinePlayers.remove(playerId);
        CompletableFuture<Optional<ResourceLocation>> loading =
                loadingPlayers.remove(playerId);
        if (loading != null) {
            loading.cancel(false);
        }
        loadFailures.remove(playerId);
    }

    public CompletionStage<OpenPreparation> prepareOpen(
            ServerPlayer player,
            ResourceLocation dialogueId
    ) {
        Objects.requireNonNull(dialogueId, "dialogueId");
        MinecraftServer server = requireServerThread(player);
        return requireLoaded(player).thenCompose(state -> {
            if (state.pendingDialogue.isPresent()) {
                return CompletableFuture.completedFuture(
                        OpenPreparation.conflict()
                );
            }
            state.pendingDialogue = Optional.of(dialogueId);
            return markPendingAsync(server, state, dialogueId)
                    .handle((ignored, error) -> new SaveOutcome(error))
                    .thenCompose(outcome -> completePreparationOnServer(
                            server,
                            player,
                            state,
                            dialogueId,
                            outcome.error
                    ));
        }).exceptionally(error -> {
            MaiMaiDialogue.LOGGER.error(
                    "Pending dialogue data is unavailable for player {}",
                    player.getUUID(),
                    unwrap(error)
            );
            return OpenPreparation.persistenceFailed();
        });
    }

    public Optional<UUID> activateRestored(
            ServerPlayer player,
            ResourceLocation dialogueId
    ) {
        requireServerThread(player);
        PlayerState state = onlinePlayers.get(player.getUUID());
        if (state == null
                || state.activeDialogue != null
                || !state.pendingDialogue.equals(Optional.of(dialogueId))) {
            return Optional.empty();
        }
        UUID token = UUID.randomUUID();
        state.activeDialogue = new ActiveDialogue(dialogueId, token);
        return Optional.of(token);
    }

    public CompletionStage<CompletionResult> complete(
            ServerPlayer player,
            ResourceLocation dialogueId,
            UUID completionToken
    ) {
        Objects.requireNonNull(dialogueId, "dialogueId");
        Objects.requireNonNull(completionToken, "completionToken");
        MinecraftServer server = requireServerThread(player);
        PlayerState state = onlinePlayers.get(player.getUUID());
        ActiveDialogue active = state == null ? null : state.activeDialogue;
        if (state == null
                || active == null
                || state.completionInProgress
                || !active.dialogueId.equals(dialogueId)
                || !active.completionToken.equals(completionToken)
                || !state.pendingDialogue.equals(Optional.of(dialogueId))) {
            return CompletableFuture.completedFuture(
                    CompletionResult.REJECTED
            );
        }

        state.completionInProgress = true;
        state.activeDialogue = null;
        return clearAsync(server, state)
                .handle((ignored, error) -> new SaveOutcome(error))
                .thenCompose(outcome -> completeClearOnServer(
                        server,
                        state,
                        outcome.error
                ));
    }

    // 只在服务器停止阶段等待，不阻塞正常 tick。
    public void awaitOutstandingWrites() {
        CompletableFuture<?>[] writes = outstandingWrites.toArray(
                CompletableFuture[]::new
        );
        try {
            CompletableFuture.allOf(writes).join();
        } catch (CompletionException exception) {
            MaiMaiDialogue.LOGGER.error(
                    "Failed to finish pending dialogue writes during server shutdown",
                    unwrap(exception)
            );
        }
    }

    private PendingDialogueRecord loadOrCreate(
            MinecraftServer server,
            UUID playerId
    ) {
        Optional<PendingDialogueRecord> loaded = store.load(server, playerId);
        if (loaded.isPresent()) {
            return loaded.orElseThrow();
        }
        PendingDialogueRecord empty = PendingDialogueRecord.empty(playerId);
        store.save(server, empty);
        return empty;
    }

    private CompletionStage<PlayerState> requireLoaded(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerState state = onlinePlayers.get(playerId);
        if (state != null) {
            return CompletableFuture.completedFuture(state);
        }
        Throwable failure = loadFailures.get(playerId);
        if (failure != null) {
            return CompletableFuture.failedFuture(failure);
        }
        return loadAsync(player).thenApply(ignored -> {
            PlayerState loaded = onlinePlayers.get(playerId);
            if (loaded == null) {
                throw new PendingDialogueDataException(
                        "Pending dialogue data is unavailable for player "
                                + playerId,
                        new IllegalStateException("Player is no longer loaded.")
                );
            }
            return loaded;
        });
    }

    private CompletableFuture<Void> markPendingAsync(
            MinecraftServer server,
            PlayerState state,
            ResourceLocation dialogueId
    ) {
        return enqueueSave(
                server,
                state,
                new PendingDialogueRecord(
                        state.playerId,
                        Optional.of(dialogueId)
                )
        );
    }

    private CompletableFuture<Void> clearAsync(
            MinecraftServer server,
            PlayerState state
    ) {
        return enqueueSave(
                server,
                state,
                PendingDialogueRecord.empty(state.playerId)
        );
    }

    private CompletableFuture<Void> enqueueSave(
            MinecraftServer server,
            PlayerState state,
            PendingDialogueRecord record
    ) {
        CompletableFuture<Void> save = state.saveTail
                .handle((ignored, previousError) -> null)
                .thenRunAsync(() -> store.save(server, record), Util.ioPool());
        state.saveTail = save;
        outstandingWrites.add(save);
        save.whenComplete((ignored, error) -> outstandingWrites.remove(save));
        return save;
    }

    private CompletionStage<OpenPreparation> completePreparationOnServer(
            MinecraftServer server,
            ServerPlayer player,
            PlayerState state,
            ResourceLocation dialogueId,
            @Nullable Throwable error
    ) {
        CompletableFuture<OpenPreparation> result = new CompletableFuture<>();
        server.execute(() -> {
            if (error != null) {
                if (onlinePlayers.get(state.playerId) == state) {
                    state.pendingDialogue = Optional.empty();
                }
                MaiMaiDialogue.LOGGER.error(
                        "Failed to persist required dialogue {} for player {}",
                        dialogueId,
                        state.playerId,
                        unwrap(error)
                );
                result.complete(OpenPreparation.persistenceFailed());
                return;
            }
            if (onlinePlayers.get(state.playerId) != state
                    || server.getPlayerList().getPlayer(state.playerId)
                    != player) {
                result.complete(OpenPreparation.playerOffline());
                return;
            }
            UUID token = UUID.randomUUID();
            state.activeDialogue = new ActiveDialogue(dialogueId, token);
            result.complete(OpenPreparation.ready(token));
        });
        return result;
    }

    private CompletionStage<CompletionResult> completeClearOnServer(
            MinecraftServer server,
            PlayerState state,
            @Nullable Throwable error
    ) {
        CompletableFuture<CompletionResult> result = new CompletableFuture<>();
        server.execute(() -> {
            state.completionInProgress = false;
            if (error != null) {
                MaiMaiDialogue.LOGGER.error(
                        "Failed to clear required dialogue for player {}",
                        state.playerId,
                        unwrap(error)
                );
                result.complete(CompletionResult.PERSISTENCE_FAILED);
                return;
            }
            state.pendingDialogue = Optional.empty();
            result.complete(CompletionResult.CLEARED);
        });
        return result;
    }

    private static MinecraftServer requireServerThread(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        MinecraftServer server = Objects.requireNonNull(
                player.getServer(),
                "player server"
        );
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "Pending dialogue service must run on the server thread."
            );
        }
        return server;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public enum CompletionResult {
        CLEARED,
        REJECTED,
        PERSISTENCE_FAILED
    }

    public enum OpenPreparationStatus {
        READY,
        CONFLICT,
        PERSISTENCE_FAILED,
        PLAYER_OFFLINE
    }

    public record OpenPreparation(
            OpenPreparationStatus status,
            Optional<UUID> completionToken
    ) {
        public OpenPreparation {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(completionToken, "completionToken");
        }

        private static OpenPreparation ready(UUID token) {
            return new OpenPreparation(
                    OpenPreparationStatus.READY,
                    Optional.of(token)
            );
        }

        private static OpenPreparation conflict() {
            return new OpenPreparation(
                    OpenPreparationStatus.CONFLICT,
                    Optional.empty()
            );
        }

        private static OpenPreparation persistenceFailed() {
            return new OpenPreparation(
                    OpenPreparationStatus.PERSISTENCE_FAILED,
                    Optional.empty()
            );
        }

        private static OpenPreparation playerOffline() {
            return new OpenPreparation(
                    OpenPreparationStatus.PLAYER_OFFLINE,
                    Optional.empty()
            );
        }
    }

    private static final class PlayerState {
        private final UUID playerId;
        private Optional<ResourceLocation> pendingDialogue;
        private CompletableFuture<Void> saveTail =
                CompletableFuture.completedFuture(null);
        @Nullable
        private ActiveDialogue activeDialogue;
        private boolean completionInProgress;

        private PlayerState(PendingDialogueRecord record) {
            playerId = record.playerId();
            pendingDialogue = record.dialogueId();
        }
    }

    private record ActiveDialogue(
            ResourceLocation dialogueId,
            UUID completionToken
    ) {
    }

    private record SaveOutcome(@Nullable Throwable error) {
    }
}
