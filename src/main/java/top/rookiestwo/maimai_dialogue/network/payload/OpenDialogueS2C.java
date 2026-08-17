package top.rookiestwo.maimai_dialogue.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record OpenDialogueS2C(
        ResourceLocation dialogueId,
        boolean mustComplete,
        Optional<UUID> completionToken
) implements CustomPacketPayload {
    public static final Type<OpenDialogueS2C> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    MaiMaiDialogue.MOD_ID,
                    "open_dialogue"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDialogueS2C>
            STREAM_CODEC = StreamCodec.of(
                    OpenDialogueS2C::encode,
                    OpenDialogueS2C::decode
            );

    public OpenDialogueS2C {
        Objects.requireNonNull(dialogueId, "dialogueId");
        Objects.requireNonNull(completionToken, "completionToken");
        if (mustComplete != completionToken.isPresent()) {
            throw new IllegalArgumentException(
                    "mustComplete must match completionToken presence."
            );
        }
    }

    public OpenDialogueS2C(ResourceLocation dialogueId) {
        this(dialogueId, false, Optional.empty());
    }

    public static OpenDialogueS2C mustComplete(
            ResourceLocation dialogueId,
            UUID completionToken
    ) {
        return new OpenDialogueS2C(
                dialogueId,
                true,
                Optional.of(completionToken)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(
            RegistryFriendlyByteBuf buffer,
            OpenDialogueS2C payload
    ) {
        buffer.writeResourceLocation(payload.dialogueId());
        buffer.writeBoolean(payload.mustComplete());
        payload.completionToken().ifPresent(buffer::writeUUID);
    }

    private static OpenDialogueS2C decode(RegistryFriendlyByteBuf buffer) {
        ResourceLocation dialogueId = buffer.readResourceLocation();
        boolean mustComplete = buffer.readBoolean();
        return new OpenDialogueS2C(
                dialogueId,
                mustComplete,
                mustComplete
                        ? Optional.of(buffer.readUUID())
                        : Optional.empty()
        );
    }
}
