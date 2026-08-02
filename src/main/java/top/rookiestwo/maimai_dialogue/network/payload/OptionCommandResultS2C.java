package top.rookiestwo.maimai_dialogue.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.network.OptionCommandStatus;

import java.util.Objects;

public record OptionCommandResultS2C(
        long requestId,
        ResourceLocation dialogueId,
        int optionIndex,
        OptionCommandStatus status
) implements CustomPacketPayload {
    public static final Type<OptionCommandResultS2C> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    MaiMaiDialogue.MOD_ID,
                    "option_command_result"
            )
    );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            OptionCommandResultS2C
            > STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeLong(payload.requestId);
                        buffer.writeResourceLocation(payload.dialogueId);
                        buffer.writeVarInt(payload.optionIndex);
                        buffer.writeVarInt(payload.status.networkId());
                    },
                    buffer -> new OptionCommandResultS2C(
                            buffer.readLong(),
                            buffer.readResourceLocation(),
                            buffer.readVarInt(),
                            OptionCommandStatus.fromNetworkId(
                                    buffer.readVarInt()
                            )
                    )
            );

    public OptionCommandResultS2C {
        Objects.requireNonNull(dialogueId, "dialogueId");
        Objects.requireNonNull(status, "status");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
