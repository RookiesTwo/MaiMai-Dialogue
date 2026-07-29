package top.rookiestwo.maimai_dialogue.progress;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.api.progress.PlayerProgressService;
import top.rookiestwo.maimai_dialogue.api.progress.ProgressChangeResult;
import top.rookiestwo.maimai_dialogue.api.progress.ProgressSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class PlayerProgressRepository implements PlayerProgressService {
    private static final int SCHEMA_VERSION = 1;
    private static final long MAX_NBT_BYTES = 1024L * 1024L;
    private static final String DIRECTORY = "data/maimai_dialogue/progress";

    private final Map<UUID, PlayerState> onlinePlayers = new HashMap<>();
    private final Map<UUID, CompletableFuture<ProgressSnapshot>> loadingPlayers =
            new HashMap<>();
    private final Map<UUID, Throwable> loadFailures = new HashMap<>();

    public CompletionStage<ProgressSnapshot> load(ServerPlayer player) {
        MinecraftServer server = requireServerThread(player);
        UUID playerId = player.getUUID();

        PlayerState loaded = onlinePlayers.get(playerId);
        if (loaded != null) {
            return CompletableFuture.completedFuture(loaded.snapshot());
        }

        CompletableFuture<ProgressSnapshot> existing = loadingPlayers.get(playerId);
        if (existing != null) {
            return existing;
        }

        loadFailures.remove(playerId);
        Path file = progressFile(server, playerId);
        CompletableFuture<ProgressSnapshot> result = new CompletableFuture<>();
        loadingPlayers.put(playerId, result);

        CompletableFuture
                .supplyAsync(() -> readState(playerId, file), Util.ioPool())
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

    public void unload(ServerPlayer player) {
        MinecraftServer server = requireServerThread(player);
        UUID playerId = player.getUUID();
        PlayerState state = onlinePlayers.remove(playerId);
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
        PlayerState state = onlinePlayers.get(player.getUUID());
        return state == null ? Optional.empty() : Optional.of(state.snapshot());
    }

    @Override
    public CompletionStage<ProgressSnapshot> snapshot(ServerPlayer player) {
        requireServerThread(player);
        return requireLoaded(player).thenApply(PlayerState::snapshot);
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
    public CompletionStage<ProgressChangeResult> add(
            ServerPlayer player,
            ProgressNode node
    ) {
        Objects.requireNonNull(node, "node");
        MinecraftServer server = requireServerThread(player);
        PlayerState state = requireLoadedNow(player);
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
    public CompletionStage<ProgressChangeResult> remove(
            ServerPlayer player,
            ProgressNode node
    ) {
        Objects.requireNonNull(node, "node");
        MinecraftServer server = requireServerThread(player);
        PlayerState state = requireLoadedNow(player);
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
        return load(player).thenApply(ignored -> requireLoadedNow(player));
    }

    private PlayerState requireLoadedNow(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerState state = onlinePlayers.get(playerId);
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
            PlayerState state,
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

    private CompletableFuture<Void> enqueueSave(
            MinecraftServer server,
            PlayerState state,
            long revision,
            Set<ProgressNode> nodes
    ) {
        Path file = progressFile(server, state.playerId);
        CompletableFuture<Void> save = state.saveTail
                .handle((ignored, previousError) -> null)
                .thenRunAsync(
                        () -> writeState(state.playerId, nodes, file),
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

    private static PlayerState readState(UUID playerId, Path file) {
        if (!Files.exists(file)) {
            return new PlayerState(playerId, Set.of());
        }

        try {
            CompoundTag root = NbtIo.readCompressed(
                    file,
                    NbtAccounter.create(MAX_NBT_BYTES)
            );
            if (root.getInt("SchemaVersion") != SCHEMA_VERSION) {
                throw new IOException("Unsupported progress schema version.");
            }
            if (!root.hasUUID("PlayerUUID")
                    || !root.getUUID("PlayerUUID").equals(playerId)) {
                throw new IOException("Progress file UUID does not match its filename.");
            }

            ListTag serializedNodes = root.getList("Nodes", Tag.TAG_STRING);
            Set<ProgressNode> nodes = new HashSet<>();
            for (Tag serializedNode : serializedNodes) {
                String value = serializedNode.getAsString();
                ProgressNode node = ProgressNode.parse(value)
                        .result()
                        .orElseThrow(() -> new IOException(
                                "Invalid progress node '" + value + "'."
                        ));
                nodes.add(node);
            }
            return new PlayerState(playerId, nodes);
        } catch (IOException | RuntimeException exception) {
            throw new ProgressDataException(
                    "Failed to read progress file " + file,
                    exception
            );
        }
    }

    private static void writeState(
            UUID playerId,
            Set<ProgressNode> nodes,
            Path file
    ) {
        Path temporaryFile = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());

            CompoundTag root = new CompoundTag();
            root.putInt("SchemaVersion", SCHEMA_VERSION);
            root.putUUID("PlayerUUID", playerId);

            List<String> sortedNodes = nodes.stream()
                    .map(ProgressNode::value)
                    .sorted()
                    .toList();
            ListTag serializedNodes = new ListTag();
            for (String node : sortedNodes) {
                serializedNodes.add(StringTag.valueOf(node));
            }
            root.put("Nodes", serializedNodes);

            NbtIo.writeCompressed(root, temporaryFile);
            Files.move(
                    temporaryFile,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException | RuntimeException exception) {
            throw new ProgressDataException(
                    "Failed to save progress file " + file,
                    exception
            );
        }
    }

    private static Path progressFile(MinecraftServer server, UUID playerId) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(DIRECTORY)
                .resolve(playerId + ".dat");
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

    private static final class PlayerState {
        private final UUID playerId;
        private final Set<ProgressNode> nodes;
        private long revision;
        private long persistedRevision;
        private CompletableFuture<Void> saveTail =
                CompletableFuture.completedFuture(null);

        private PlayerState(UUID playerId, Set<ProgressNode> nodes) {
            this.playerId = playerId;
            this.nodes = new HashSet<>(nodes);
        }

        private ProgressSnapshot snapshot() {
            return new ProgressSnapshot(playerId, nodes);
        }
    }
}
