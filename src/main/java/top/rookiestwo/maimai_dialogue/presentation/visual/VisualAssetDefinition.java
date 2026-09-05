package top.rookiestwo.maimai_dialogue.presentation.visual;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;

/**
 * Reusable image variants and sampling shared by VisualObject instances.
 */
public record VisualAssetDefinition(
        Map<String, ResourceLocation> variants,
        VisualSampling sampling
) {
    private static final Codec<Map<String, ResourceLocation>> VARIANTS_CODEC =
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC);

    private static final Codec<VisualAssetDefinition> BASE_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    VARIANTS_CODEC.fieldOf("variants")
                            .forGetter(VisualAssetDefinition::variants),
                    VisualSampling.CODEC.optionalFieldOf(
                                    "sampling",
                                    VisualSampling.LINEAR
                            )
                            .forGetter(VisualAssetDefinition::sampling)
            ).apply(instance, VisualAssetDefinition::new));

    public static final Codec<VisualAssetDefinition> CODEC =
            BASE_CODEC.flatXmap(
                    VisualAssetDefinition::validate,
                    VisualAssetDefinition::validate
            );

    public VisualAssetDefinition {
        Objects.requireNonNull(variants, "variants");
        Objects.requireNonNull(sampling, "sampling");
        variants = Map.copyOf(variants);
    }

    private static DataResult<VisualAssetDefinition> validate(
            VisualAssetDefinition definition
    ) {
        if (definition.variants().isEmpty()) {
            return DataResult.error(
                    () -> "VisualAsset variants must not be empty."
            );
        }
        for (String variant : definition.variants().keySet()) {
            if (!variant.matches("[a-z0-9_-]+")) {
                return DataResult.error(
                        () -> "Invalid VisualAsset variant ID '"
                                + variant + "'."
                );
            }
        }
        return DataResult.success(definition);
    }
}
