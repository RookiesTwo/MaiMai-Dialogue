package top.rookiestwo.maimai_dialogue.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record QueryDialogueAccessC2S(
        long requestId,
        List<ResourceLocation> dialogueIds
) implements CustomPacketPayload {
    public static final int MAX_DIALOGUE_IDS = 256;

    public static final Type<QueryDialogueAccessC2S> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    MaiMaiDialogue.MOD_ID,
                    "query_dialogue_access"
            )
    );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            QueryDialogueAccessC2S
            > STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.dialogueIds.size());
                        buffer.writeLong(payload.requestId);
                        for (ResourceLocation dialogueId : payload.dialogueIds) {
                            buffer.writeResourceLocation(dialogueId);
                        }
                    },
                    buffer -> {
                        int size = buffer.readVarInt();
                        if (size < 0 || size > MAX_DIALOGUE_IDS) {
                            throw new IllegalArgumentException(
                                    "Invalid dialogue access query size: " + size
                            );
                        }
                        long requestId = buffer.readLong();
                        var dialogueIds = new java.util.ArrayList<ResourceLocation>(
                                size
                        );
                        for (int index = 0; index < size; index++) {
                            dialogueIds.add(buffer.readResourceLocation());
                        }
                        return new QueryDialogueAccessC2S(
                                requestId,
                                dialogueIds
                        );
                    }
            );

    public QueryDialogueAccessC2S {
        Objects.requireNonNull(dialogueIds, "dialogueIds");
        dialogueIds = List.copyOf(new LinkedHashSet<>(dialogueIds));
        if (dialogueIds.size() > MAX_DIALOGUE_IDS) {
            throw new IllegalArgumentException(
                    "A dialogue access query may contain at most "
                            + MAX_DIALOGUE_IDS + " IDs."
            );
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
