package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record CrtFilter(
        float curvature,
        float scanlineStrength,
        float maskStrength,
        float chromaticAberration,
        float vignette,
        float noise,
        float flicker,
        float bloom
) implements SceneFilter {
    private static final Codec<Float> UNIT_CODEC = Codec.FLOAT.validate(
            value -> value >= 0.0F && value <= 1.0F
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "CRT filter values must be between 0 and 1."
                    )
    );
    private static final Codec<Float> ABERRATION_CODEC = Codec.FLOAT.validate(
            value -> value >= 0.0F && value <= 4.0F
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "CRT chromatic_aberration must be between 0 and 4."
                    )
    );

    public static final MapCodec<CrtFilter> CODEC =
            RecordCodecBuilder.mapCodec(instance ->
                    instance.group(
                            UNIT_CODEC.optionalFieldOf("curvature", 0.08F)
                                    .forGetter(CrtFilter::curvature),
                            UNIT_CODEC.optionalFieldOf(
                                            "scanline_strength",
                                            0.22F
                                    )
                                    .forGetter(CrtFilter::scanlineStrength),
                            UNIT_CODEC.optionalFieldOf(
                                            "mask_strength",
                                            0.12F
                                    )
                                    .forGetter(CrtFilter::maskStrength),
                            ABERRATION_CODEC.optionalFieldOf(
                                            "chromatic_aberration",
                                            1.0F
                                    )
                                    .forGetter(CrtFilter::chromaticAberration),
                            UNIT_CODEC.optionalFieldOf("vignette", 0.18F)
                                    .forGetter(CrtFilter::vignette),
                            UNIT_CODEC.optionalFieldOf("noise", 0.025F)
                                    .forGetter(CrtFilter::noise),
                            UNIT_CODEC.optionalFieldOf("flicker", 0.01F)
                                    .forGetter(CrtFilter::flicker),
                            UNIT_CODEC.optionalFieldOf("bloom", 0.1F)
                                    .forGetter(CrtFilter::bloom)
                    ).apply(instance, CrtFilter::new)
            );

    @Override
    public Type type() {
        return Type.CRT;
    }
}
