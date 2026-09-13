package top.rookiestwo.maimai_dialogue.audio;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

public record TypewriterSound(boolean enabled, SoundSpec sound, int minIntervalMs) {
    public static final ResourceLocation DEFAULT_SOUND = ResourceLocation.fromNamespaceAndPath("maimai_dialogue", "ui.typewriter");
    public static final TypewriterSound DEFAULT = new TypewriterSound(true, new SoundSpec(DEFAULT_SOUND, 0.15F, 1), 50);
    public static final TypewriterSound SILENT = new TypewriterSound(false, DEFAULT.sound(), 50);

    private static final Codec<TypewriterSound> OBJECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("sound", DEFAULT_SOUND).forGetter(value -> value.sound().sound()),
            AudioCodecs.VOLUME.optionalFieldOf("volume", 0.15F).forGetter(value -> value.sound().volume()),
            AudioCodecs.PITCH.optionalFieldOf("pitch", 1.0F).forGetter(value -> value.sound().pitch()),
            AudioCodecs.TIME.optionalFieldOf("min_interval_ms", 50).forGetter(TypewriterSound::minIntervalMs)
    ).apply(instance, (sound, volume, pitch, interval) -> new TypewriterSound(true, new SoundSpec(sound, volume, pitch), interval)));

    public static final Codec<TypewriterSound> CODEC = Codec.either(
            Codec.BOOL.validate(value -> !value ? DataResult.success(false)
                    : DataResult.error(() -> "typewriter_sound must be false or an object.")), OBJECT_CODEC
    ).xmap(value -> value.map(ignored -> SILENT, sound -> sound),
            value -> value.enabled() ? Either.right(value) : Either.left(false));

    public TypewriterSound {
        Objects.requireNonNull(sound, "sound");
        if (minIntervalMs < 0 || minIntervalMs > 60_000) throw new IllegalArgumentException("Invalid typewriter sound interval.");
    }
}
