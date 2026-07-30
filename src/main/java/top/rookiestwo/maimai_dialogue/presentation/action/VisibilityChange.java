package top.rookiestwo.maimai_dialogue.presentation.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record VisibilityChange(
        float at,
        boolean visible
) {
    private static final Codec<Float> TIME_CODEC = Codec.FLOAT.validate(
            value -> value >= 0.0F && value <= 1.0F
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "Visibility change time must be between 0 and 1."
                    )
    );

    public static final Codec<VisibilityChange> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    TIME_CODEC.optionalFieldOf("at", 0.0F)
                            .forGetter(VisibilityChange::at),
                    Codec.BOOL.fieldOf("value")
                            .forGetter(VisibilityChange::visible)
            ).apply(instance, VisibilityChange::new));
}
