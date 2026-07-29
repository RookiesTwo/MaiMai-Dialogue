package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record SetSpeaker(ResourceLocation id) implements SpeakerOperation {
    public static final MapCodec<SetSpeaker> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(SetSpeaker::id)
            ).apply(instance, SetSpeaker::new)
    );

    public SetSpeaker {
        Objects.requireNonNull(id, "id");
    }

    @Override
    public Type type() {
        return Type.SET;
    }
}
