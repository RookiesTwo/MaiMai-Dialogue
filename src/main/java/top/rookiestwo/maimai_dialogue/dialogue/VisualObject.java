package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;

public record VisualObject(
        Map<String, ResourceLocation> variants,
        String initialVariant,
        float x,
        float y,
        VisualAnchor anchor,
        float scale,
        VisualSampling sampling,
        float opacity,
        boolean visible,
        int zIndex
) {
    private static final Codec<Float> SCALE_CODEC = Codec.FLOAT.validate(
            value -> value > 0.0F
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "VisualObject scale must be greater than 0."
                    )
    );
    private static final Codec<Float> OPACITY_CODEC = Codec.FLOAT.validate(
            value -> value >= 0.0F && value <= 1.0F
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "VisualObject opacity must be between 0 and 1."
                    )
    );
    private static final Codec<Map<String, ResourceLocation>> VARIANTS_CODEC =
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC);

    private static final Codec<VisualObject> BASE_CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            VARIANTS_CODEC.fieldOf("variants")
                                    .forGetter(VisualObject::variants),
                            Codec.STRING.fieldOf("initial_variant")
                                    .forGetter(VisualObject::initialVariant),
                            Codec.FLOAT.optionalFieldOf("x", 0.5F)
                                    .forGetter(VisualObject::x),
                            Codec.FLOAT.optionalFieldOf("y", 0.5F)
                                    .forGetter(VisualObject::y),
                            VisualAnchor.CODEC.optionalFieldOf(
                                            "anchor",
                                            VisualAnchor.CENTER
                                    )
                                    .forGetter(VisualObject::anchor),
                            SCALE_CODEC.optionalFieldOf("scale", 1.0F)
                                    .forGetter(VisualObject::scale),
                            VisualSampling.CODEC.optionalFieldOf(
                                            "sampling",
                                            VisualSampling.LINEAR
                                    )
                                    .forGetter(VisualObject::sampling),
                            OPACITY_CODEC.optionalFieldOf("opacity", 1.0F)
                                    .forGetter(VisualObject::opacity),
                            Codec.BOOL.optionalFieldOf("visible", true)
                                    .forGetter(VisualObject::visible),
                            Codec.INT.optionalFieldOf("z_index", 0)
                                    .forGetter(VisualObject::zIndex)
                    ).apply(instance, VisualObject::new)
            );

    public static final Codec<VisualObject> CODEC = BASE_CODEC.flatXmap(
            VisualObject::validate,
            DataResult::success
    );

    public VisualObject {
        Objects.requireNonNull(variants, "variants");
        Objects.requireNonNull(initialVariant, "initialVariant");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(sampling, "sampling");
        variants = Map.copyOf(variants);
    }

    public ResourceLocation initialImage() {
        return variants.get(initialVariant);
    }

    private static DataResult<VisualObject> validate(VisualObject object) {
        if (object.variants.isEmpty()) {
            return DataResult.error(
                    () -> "VisualObject variants must not be empty."
            );
        }
        for (String variant : object.variants.keySet()) {
            if (!variant.matches("[a-z0-9_-]+")) {
                return DataResult.error(
                        () -> "Invalid VisualObject variant ID '"
                                + variant + "'."
                );
            }
        }
        if (!object.variants.containsKey(object.initialVariant)) {
            return DataResult.error(
                    () -> "VisualObject initial_variant '"
                            + object.initialVariant
                            + "' is not present in variants."
            );
        }
        return DataResult.success(object);
    }
}
