package top.rookiestwo.maimai_dialogue.audio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public final class AudioCodecs {
    public static final Codec<Float> VOLUME = number("volume", 0, 1);
    public static final Codec<Float> PITCH = number("pitch", 0.5F, 2);
    public static final Codec<Integer> TIME = Codec.intRange(0, 60_000);

    private AudioCodecs() {}

    private static Codec<Float> number(String name, float min, float max) {
        return Codec.FLOAT.validate(value -> Float.isFinite(value) && value >= min && value <= max
                ? DataResult.success(value)
                : DataResult.error(() -> name + " must be between " + min + " and " + max + "."));
    }

    public static void validate(float volume, float pitch) {
        if (!Float.isFinite(volume) || volume < 0 || volume > 1
                || !Float.isFinite(pitch) || pitch < 0.5F || pitch > 2) {
            throw new IllegalArgumentException("Invalid sound volume or pitch.");
        }
    }
}
