package top.rookiestwo.maimai_dialogue.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;

import java.util.Objects;

public record ExecuteOptionCommandC2S(
        long requestId,
        ResourceLocation dialogueId,
        int optionIndex
) implements CustomPacketPayload {
    public static final Type<ExecuteOptionCommandC2S> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    MaiMaiDialogue.MOD_ID,
                    "execute_option_command"
            )
    );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ExecuteOptionCommandC2S
            > STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeLong(payload.requestId);
                        buffer.writeResourceLocation(payload.dialogueId);
                        buffer.writeVarInt(payload.optionIndex);
                    },
                    buffer -> new ExecuteOptionCommandC2S(
                            buffer.readLong(),
                            buffer.readResourceLocation(),
                            buffer.readVarInt()
                    )
            );

    public ExecuteOptionCommandC2S {
        Objects.requireNonNull(dialogueId, "dialogueId");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
