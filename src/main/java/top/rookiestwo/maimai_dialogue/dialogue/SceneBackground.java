package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;

public record SceneBackground(
        Map<String, ResourceLocation> variants,
        String initialVariant,
        BackgroundFit fit,
        float opacity
) {
    private static final Codec<Float> OPACITY_CODEC = Codec.FLOAT.validate(
            value -> value >= 0.0F && value <= 1.0F
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "Background opacity must be between 0 and 1."
                    )
    );

    private static final Codec<Map<String, ResourceLocation>> VARIANTS_CODEC =
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC);

    private static final Codec<SceneBackground> BASE_CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            VARIANTS_CODEC.fieldOf("variants")
                                    .forGetter(SceneBackground::variants),
                            Codec.STRING.optionalFieldOf(
                                            "initial_variant",
                                            "default"
                                    )
                                    .forGetter(SceneBackground::initialVariant),
                            BackgroundFit.CODEC
                                    .optionalFieldOf(
                                            "fit",
                                            BackgroundFit.COVER
                                    )
                                    .forGetter(SceneBackground::fit),
                            OPACITY_CODEC.optionalFieldOf("opacity", 1.0F)
                                    .forGetter(SceneBackground::opacity)
                    ).apply(instance, SceneBackground::new)
            );

    public static final Codec<SceneBackground> CODEC = BASE_CODEC.flatXmap(
            SceneBackground::validate,
            DataResult::success
    );

    public SceneBackground {
        Objects.requireNonNull(variants, "variants");
        Objects.requireNonNull(initialVariant, "initialVariant");
        Objects.requireNonNull(fit, "fit");
        variants = Map.copyOf(variants);
    }

    public ResourceLocation initialImage() {
        return variants.get(initialVariant);
    }

    private static DataResult<SceneBackground> validate(
            SceneBackground background
    ) {
        if (background.variants.isEmpty()) {
            return DataResult.error(
                    () -> "Background variants must not be empty."
            );
        }
        for (String variant : background.variants.keySet()) {
            if (!variant.matches("[a-z0-9_-]+")) {
                return DataResult.error(
                        () -> "Invalid Background variant ID '"
                                + variant + "'."
                );
            }
        }
        if (!background.variants.containsKey(background.initialVariant)) {
            return DataResult.error(
                    () -> "Background initial_variant '"
                            + background.initialVariant
                            + "' is not present in variants."
            );
        }
        return DataResult.success(background);
    }
}
