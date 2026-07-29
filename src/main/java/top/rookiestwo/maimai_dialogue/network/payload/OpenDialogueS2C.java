package top.rookiestwo.maimai_dialogue.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;

import java.util.Objects;

public record OpenDialogueS2C(
        ResourceLocation dialogueId
) implements CustomPacketPayload {
    public static final Type<OpenDialogueS2C> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    MaiMaiDialogue.MOD_ID,
                    "open_dialogue"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDialogueS2C>
            STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) ->
                            buffer.writeResourceLocation(payload.dialogueId),
                    buffer -> new OpenDialogueS2C(
                            buffer.readResourceLocation()
                    )
            );

    public OpenDialogueS2C {
        Objects.requireNonNull(dialogueId, "dialogueId");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
