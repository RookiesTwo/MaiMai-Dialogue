package top.rookiestwo.maimai_dialogue.presentation.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

public record VariantChange(
        float at,
        String variant
) {
    private static final Codec<Float> TIME_CODEC = Codec.FLOAT.validate(
            value -> value >= 0.0F && value <= 1.0F
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "Variant change time must be between 0 and 1."
                    )
    );
    private static final Codec<String> VARIANT_CODEC = Codec.STRING.validate(
            value -> value.matches("[a-z0-9_-]+")
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "Invalid VisualObject variant ID '"
                                    + value + "'."
                    )
    );

    public static final Codec<VariantChange> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    TIME_CODEC.optionalFieldOf("at", 0.0F)
                            .forGetter(VariantChange::at),
                    VARIANT_CODEC.fieldOf("value")
                            .forGetter(VariantChange::variant)
            ).apply(instance, VariantChange::new));

    public VariantChange {
        Objects.requireNonNull(variant, "variant");
    }
}
