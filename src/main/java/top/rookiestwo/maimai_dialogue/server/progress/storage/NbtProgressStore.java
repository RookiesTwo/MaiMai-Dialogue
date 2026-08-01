package top.rookiestwo.maimai_dialogue.server.progress.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import top.rookiestwo.maimai_dialogue.progress.ProgressDataException;
import top.rookiestwo.maimai_dialogue.progress.ProgressNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class NbtProgressStore implements ProgressStore {
    private static final int SCHEMA_VERSION = 1;
    private static final long MAX_NBT_BYTES = 1024L * 1024L;
    private static final String DIRECTORY = "data/maimai_dialogue/progress";
    private final Function<MinecraftServer, Path> worldRoot;

    public NbtProgressStore() {
        this(server -> server.getWorldPath(LevelResource.ROOT));
    }

    NbtProgressStore(Function<MinecraftServer, Path> worldRoot) {
        this.worldRoot = java.util.Objects.requireNonNull(
                worldRoot,
                "worldRoot"
        );
    }

    @Override
    // 从玩家对应的压缩 NBT 文件中读取全部 ProgressNode。
    public Set<ProgressNode> load(
            MinecraftServer server,
            UUID playerId
    ) {
        Path file = progressFile(server, playerId);
        if (!Files.exists(file)) {
            return Set.of();
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
                throw new IOException(
                        "Progress file UUID does not match its filename."
                );
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
            return Set.copyOf(nodes);
        } catch (IOException | RuntimeException exception) {
            throw new ProgressDataException(
                    "Failed to read progress file " + file,
                    exception
            );
        }
    }

    @Override
    // 把稳定排序后的节点写入临时文件，再原子替换正式文件。
    public void save(
            MinecraftServer server,
            UUID playerId,
            Set<ProgressNode> nodes
    ) {
        Path file = progressFile(server, playerId);
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

    private Path progressFile(
            MinecraftServer server,
            UUID playerId
    ) {
        return worldRoot.apply(server)
                .resolve(DIRECTORY)
                .resolve(playerId + ".dat");
    }
}
