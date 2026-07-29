package top.rookiestwo.maimai_dialogue.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;

import java.util.Objects;

public record RequestDialogueC2S(
        long requestId,
        ResourceLocation dialogueId
) implements CustomPacketPayload {
    public static final Type<RequestDialogueC2S> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    MaiMaiDialogue.MOD_ID,
                    "request_dialogue"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestDialogueC2S>
            STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeLong(payload.requestId);
                        buffer.writeResourceLocation(payload.dialogueId);
                    },
                    buffer -> new RequestDialogueC2S(
                            buffer.readLong(),
                            buffer.readResourceLocation()
                    )
            );

    public RequestDialogueC2S {
        Objects.requireNonNull(dialogueId, "dialogueId");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
