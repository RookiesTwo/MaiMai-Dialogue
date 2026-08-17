package top.rookiestwo.maimai_dialogue.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;

import java.util.Objects;
import java.util.UUID;

public record CompleteRequiredDialogueC2S(
        ResourceLocation dialogueId,
        UUID completionToken
) implements CustomPacketPayload {
    public static final Type<CompleteRequiredDialogueC2S> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    MaiMaiDialogue.MOD_ID,
                    "complete_required_dialogue"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf,
            CompleteRequiredDialogueC2S> STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeResourceLocation(payload.dialogueId());
                        buffer.writeUUID(payload.completionToken());
                    },
                    buffer -> new CompleteRequiredDialogueC2S(
                            buffer.readResourceLocation(),
                            buffer.readUUID()
                    )
            );

    public CompleteRequiredDialogueC2S {
        Objects.requireNonNull(dialogueId, "dialogueId");
        Objects.requireNonNull(completionToken, "completionToken");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
