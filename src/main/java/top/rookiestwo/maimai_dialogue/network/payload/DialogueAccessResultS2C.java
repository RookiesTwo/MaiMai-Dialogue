package top.rookiestwo.maimai_dialogue.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.network.DialogueAccessEntry;
import top.rookiestwo.maimai_dialogue.network.DialogueAccessStatus;

import java.util.List;
import java.util.Objects;

public record DialogueAccessResultS2C(
        long requestId,
        List<DialogueAccessEntry> entries
) implements CustomPacketPayload {
    public static final Type<DialogueAccessResultS2C> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    MaiMaiDialogue.MOD_ID,
                    "dialogue_access_result"
            )
    );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            DialogueAccessResultS2C
            > STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeLong(payload.requestId);
                        buffer.writeVarInt(payload.entries.size());
                        for (DialogueAccessEntry entry : payload.entries) {
                            buffer.writeResourceLocation(entry.dialogueId());
                            buffer.writeVarInt(entry.status().networkId());
                        }
                    },
                    buffer -> {
                        long requestId = buffer.readLong();
                        int size = buffer.readVarInt();
                        if (size < 0
                                || size > QueryDialogueAccessC2S.MAX_DIALOGUE_IDS) {
                            throw new IllegalArgumentException(
                                    "Invalid dialogue access result size: " + size
                            );
                        }
                        var entries = new java.util.ArrayList<DialogueAccessEntry>(
                                size
                        );
                        for (int index = 0; index < size; index++) {
                            entries.add(new DialogueAccessEntry(
                                    buffer.readResourceLocation(),
                                    DialogueAccessStatus.fromNetworkId(
                                            buffer.readVarInt()
                                    )
                            ));
                        }
                        return new DialogueAccessResultS2C(
                                requestId,
                                entries
                        );
                    }
            );

    public DialogueAccessResultS2C {
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);
        if (entries.size() > QueryDialogueAccessC2S.MAX_DIALOGUE_IDS) {
            throw new IllegalArgumentException(
                    "A dialogue access result may contain at most "
                            + QueryDialogueAccessC2S.MAX_DIALOGUE_IDS
                            + " entries."
            );
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
