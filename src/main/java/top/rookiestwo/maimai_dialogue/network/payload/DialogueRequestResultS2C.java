package top.rookiestwo.maimai_dialogue.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.network.DialogueAccessStatus;

import java.util.Objects;

public record DialogueRequestResultS2C(
        long requestId,
        ResourceLocation dialogueId,
        DialogueAccessStatus status
) implements CustomPacketPayload {
    public static final Type<DialogueRequestResultS2C> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    MaiMaiDialogue.MOD_ID,
                    "dialogue_request_result"
            )
    );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            DialogueRequestResultS2C
            > STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeLong(payload.requestId);
                        buffer.writeResourceLocation(payload.dialogueId);
                        buffer.writeVarInt(payload.status.networkId());
                    },
                    buffer -> new DialogueRequestResultS2C(
                            buffer.readLong(),
                            buffer.readResourceLocation(),
                            DialogueAccessStatus.fromNetworkId(
                                    buffer.readVarInt()
                            )
                    )
            );

    public DialogueRequestResultS2C {
        Objects.requireNonNull(dialogueId, "dialogueId");
        Objects.requireNonNull(status, "status");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
