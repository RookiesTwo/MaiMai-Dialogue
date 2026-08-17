package top.rookiestwo.maimai_dialogue.server.pending.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import top.rookiestwo.maimai_dialogue.server.pending.PendingDialogueDataException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class NbtPendingDialogueStore implements PendingDialogueStore {
    private static final int SCHEMA_VERSION = 1;
    private static final long MAX_NBT_BYTES = 64L * 1024L;
    private static final String DIRECTORY =
            "data/maimai_dialogue/pending_dialogues";
    private static final String PENDING_DIALOGUE = "PendingDialogue";

    private final Function<MinecraftServer, Path> worldRoot;
    private final NbtWriter writer;

    public NbtPendingDialogueStore() {
        this(
                server -> server.getWorldPath(LevelResource.ROOT),
                IOUtilities::writeNbtCompressed
        );
    }

    NbtPendingDialogueStore(
            Function<MinecraftServer, Path> worldRoot,
            NbtWriter writer
    ) {
        this.worldRoot = java.util.Objects.requireNonNull(
                worldRoot,
                "worldRoot"
        );
        this.writer = java.util.Objects.requireNonNull(writer, "writer");
    }

    @Override
    public Optional<PendingDialogueRecord> load(
            MinecraftServer server,
            UUID playerId
    ) {
        Path file = recordFile(server, playerId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            CompoundTag root = NbtIo.readCompressed(
                    file,
                    NbtAccounter.create(MAX_NBT_BYTES)
            );
            if (root.getInt("SchemaVersion") != SCHEMA_VERSION) {
                throw new IOException(
                        "Unsupported pending dialogue schema version."
                );
            }
            if (!root.hasUUID("PlayerUUID")
                    || !root.getUUID("PlayerUUID").equals(playerId)) {
                throw new IOException(
                        "Pending dialogue UUID does not match its filename."
                );
            }

            Optional<ResourceLocation> dialogueId = Optional.empty();
            if (root.contains(PENDING_DIALOGUE)) {
                String value = root.getString(PENDING_DIALOGUE);
                ResourceLocation parsed = ResourceLocation.tryParse(value);
                if (parsed == null) {
                    throw new IOException(
                            "Invalid pending dialogue ID '" + value + "'."
                    );
                }
                dialogueId = Optional.of(parsed);
            }
            return Optional.of(new PendingDialogueRecord(
                    playerId,
                    dialogueId
            ));
        } catch (IOException | RuntimeException exception) {
            throw new PendingDialogueDataException(
                    "Failed to read pending dialogue file " + file,
                    exception
            );
        }
    }

    @Override
    public void save(
            MinecraftServer server,
            PendingDialogueRecord record
    ) {
        Path file = recordFile(server, record.playerId());
        try {
            Files.createDirectories(file.getParent());
            CompoundTag root = new CompoundTag();
            root.putInt("SchemaVersion", SCHEMA_VERSION);
            root.putUUID("PlayerUUID", record.playerId());
            record.dialogueId().ifPresent(dialogueId ->
                    root.putString(PENDING_DIALOGUE, dialogueId.toString())
            );
            // NeoForge 负责临时文件、落盘和安全替换。
            writer.write(root, file);
        } catch (IOException | RuntimeException exception) {
            throw new PendingDialogueDataException(
                    "Failed to save pending dialogue file " + file,
                    exception
            );
        }
    }

    Path recordFile(MinecraftServer server, UUID playerId) {
        return worldRoot.apply(server)
                .resolve(DIRECTORY)
                .resolve(playerId + ".dat");
    }

    @FunctionalInterface
    interface NbtWriter {
        void write(CompoundTag tag, Path path) throws IOException;
    }
}
