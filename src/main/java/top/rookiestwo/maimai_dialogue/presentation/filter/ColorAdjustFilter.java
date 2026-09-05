package top.rookiestwo.maimai_dialogue.presentation.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record ColorAdjustFilter(
        float brightness,
        float contrast,
        float saturation,
        Optional<SceneColor> tint
) implements SceneFilter {
    private static final Codec<Float> BRIGHTNESS_CODEC = range(
            -1.0F,
            1.0F,
            "brightness"
    );
    private static final Codec<Float> MULTIPLIER_CODEC = range(
            0.0F,
            2.0F,
            "contrast/saturation"
    );

    public static final MapCodec<ColorAdjustFilter> CODEC =
            RecordCodecBuilder.mapCodec(instance ->
                    instance.group(
                            BRIGHTNESS_CODEC.optionalFieldOf(
                                            "brightness",
                                            0.0F
                                    )
                                    .forGetter(ColorAdjustFilter::brightness),
                            MULTIPLIER_CODEC.optionalFieldOf(
                                            "contrast",
                                            1.0F
                                    )
                                    .forGetter(ColorAdjustFilter::contrast),
                            MULTIPLIER_CODEC.optionalFieldOf(
                                            "saturation",
                                            1.0F
                                    )
                                    .forGetter(ColorAdjustFilter::saturation),
                            SceneColor.CODEC.optionalFieldOf("tint")
                                    .forGetter(ColorAdjustFilter::tint)
                    ).apply(instance, ColorAdjustFilter::new)
            );

    public ColorAdjustFilter {
        tint = tint == null ? Optional.empty() : tint;
    }

    @Override
    public Type type() {
        return Type.COLOR_ADJUST;
    }

    private static Codec<Float> range(
            float minimum,
            float maximum,
            String name
    ) {
        return Codec.FLOAT.validate(value ->
                value >= minimum && value <= maximum
                        ? DataResult.success(value)
                        : DataResult.error(
                                () -> name + " must be between "
                                        + minimum + " and " + maximum + "."
                        )
        );
    }
}
