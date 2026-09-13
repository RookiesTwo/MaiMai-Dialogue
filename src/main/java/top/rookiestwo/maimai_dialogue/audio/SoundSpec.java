package top.rookiestwo.maimai_dialogue.audio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

public record SoundSpec(ResourceLocation sound, float volume, float pitch) {
    public static final Codec<SoundSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("sound").forGetter(SoundSpec::sound),
            AudioCodecs.VOLUME.optionalFieldOf("volume", 1.0F).forGetter(SoundSpec::volume),
            AudioCodecs.PITCH.optionalFieldOf("pitch", 1.0F).forGetter(SoundSpec::pitch)
    ).apply(instance, SoundSpec::new));

    public SoundSpec {
        Objects.requireNonNull(sound, "sound");
        AudioCodecs.validate(volume, pitch);
    }
}
