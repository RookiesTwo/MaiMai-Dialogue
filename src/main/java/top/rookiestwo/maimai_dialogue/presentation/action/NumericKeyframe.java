package top.rookiestwo.maimai_dialogue.presentation.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record NumericKeyframe(
        float at,
        float value
) {
    private static final Codec<Float> TIME_CODEC = Codec.FLOAT.validate(
            value -> value >= 0.0F && value <= 1.0F
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "Keyframe time must be between 0 and 1."
                    )
    );

    public static final Codec<NumericKeyframe> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    TIME_CODEC.fieldOf("at")
                            .forGetter(NumericKeyframe::at),
                    Codec.FLOAT.fieldOf("value")
                            .forGetter(NumericKeyframe::value)
            ).apply(instance, NumericKeyframe::new));
}
