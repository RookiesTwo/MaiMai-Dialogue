package top.rookiestwo.maimai_dialogue.audio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.Optional;

public record BgmOperation(String type, Optional<ResourceLocation> sound, float volume, boolean loop, int fadeMs) {
    private static final Codec<String> TYPE_CODEC = Codec.STRING.validate(value -> value.equals("play") || value.equals("stop")
            ? DataResult.success(value) : DataResult.error(() -> "BGM type must be play or stop."));
    public static final Codec<BgmOperation> CODEC = RecordCodecBuilder.<BgmOperation>create(instance -> instance.group(
            TYPE_CODEC.fieldOf("type").forGetter(BgmOperation::type),
            ResourceLocation.CODEC.optionalFieldOf("sound").forGetter(BgmOperation::sound),
            AudioCodecs.VOLUME.optionalFieldOf("volume", 1.0F).forGetter(BgmOperation::volume),
            Codec.BOOL.optionalFieldOf("loop", true).forGetter(BgmOperation::loop),
            AudioCodecs.TIME.optionalFieldOf("fade_ms", 500).forGetter(BgmOperation::fadeMs)
    ).apply(instance, BgmOperation::new)).validate(value -> value.isPlay() == value.sound().isPresent()
            ? DataResult.success(value) : DataResult.error(() -> "BGM play requires sound; stop must omit sound."));

    public BgmOperation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sound, "sound");
        if (!type.equals("play") && !type.equals("stop")) throw new IllegalArgumentException("Invalid BGM type.");
        AudioCodecs.validate(volume, 1);
        if (fadeMs < 0 || fadeMs > 60_000) throw new IllegalArgumentException("Invalid BGM fade time.");
    }

    public boolean isPlay() { return type.equals("play"); }
}
