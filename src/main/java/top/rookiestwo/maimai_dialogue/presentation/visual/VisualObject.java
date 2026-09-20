package top.rookiestwo.maimai_dialogue.presentation.visual;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record VisualObject(
        Optional<ResourceLocation> asset,
        Map<String, ResourceLocation> variants,
        String initialVariant,
        float x,
        float y,
        VisualAnchor anchor,
        float scale,
        Optional<VisualSampling> samplingOverride,
        float opacity,
        boolean visible,
        int zIndex,
        float scaleX,
        float scaleY
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
    private static final Codec<Float> AXIS_SCALE_CODEC = Codec.FLOAT.validate(value ->
            Float.isFinite(value) && value > 0 ? DataResult.success(value)
                    : DataResult.error(() -> "VisualObject axis scale must be finite and greater than 0."));
    private static final Codec<Map<String, ResourceLocation>> VARIANTS_CODEC =
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC);

    private static final Codec<VisualObject> BASE_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("asset")
                            .forGetter(VisualObject::asset),
                    VARIANTS_CODEC.optionalFieldOf("variants")
                            .forGetter(object -> object.asset().isEmpty()
                                    ? Optional.of(object.variants())
                                    : Optional.empty()),
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
                    VisualSampling.CODEC.optionalFieldOf("sampling")
                            .forGetter(VisualObject::samplingOverride),
                    OPACITY_CODEC.optionalFieldOf("opacity", 1.0F)
                            .forGetter(VisualObject::opacity),
                    Codec.BOOL.optionalFieldOf("visible", true)
                            .forGetter(VisualObject::visible),
                    Codec.INT.optionalFieldOf("z_index", 0)
                            .forGetter(VisualObject::zIndex),
                    AXIS_SCALE_CODEC.optionalFieldOf("scale_x", 1.0F).forGetter(VisualObject::scaleX),
                    AXIS_SCALE_CODEC.optionalFieldOf("scale_y", 1.0F).forGetter(VisualObject::scaleY)
            ).apply(instance, VisualObject::decode));

    public static final Codec<VisualObject> CODEC = BASE_CODEC.flatXmap(
            VisualObject::validate,
            VisualObject::validate
    );

    public VisualObject {
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(variants, "variants");
        Objects.requireNonNull(initialVariant, "initialVariant");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(samplingOverride, "samplingOverride");
        variants = Map.copyOf(variants);
    }

    /**
     * Existing uniform-scale construction remains equivalent to scale_x = scale_y = 1.
     */
    public VisualObject(Optional<ResourceLocation> asset, Map<String, ResourceLocation> variants, String initialVariant,
                        float x, float y, VisualAnchor anchor, float scale, Optional<VisualSampling> samplingOverride,
                        float opacity, boolean visible, int zIndex) {
        this(asset, variants, initialVariant, x, y, anchor, scale, samplingOverride, opacity, visible, zIndex, 1, 1);
    }

    /**
     * Backward-compatible constructor for inline VisualObject definitions.
     */
    public VisualObject(
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
        this(
                Optional.empty(),
                variants,
                initialVariant,
                x,
                y,
                anchor,
                scale,
                Optional.of(Objects.requireNonNull(sampling, "sampling")),
                opacity,
                visible,
                zIndex
        );
    }

    public boolean referencesAsset() {
        return asset.isPresent();
    }

    public VisualSampling sampling() {
        return samplingOverride.orElse(VisualSampling.LINEAR);
    }

    public ResourceLocation initialImage() {
        return variants.get(initialVariant);
    }

    public DataResult<VisualObject> resolve(
            VisualAssetDefinition definition
    ) {
        Objects.requireNonNull(definition, "definition");
        if (asset.isEmpty()) {
            return DataResult.success(this);
        }
        VisualObject resolved = new VisualObject(
                Optional.empty(),
                definition.variants(),
                initialVariant,
                x,
                y,
                anchor,
                scale,
                Optional.of(samplingOverride.orElse(definition.sampling())),
                opacity,
                visible,
                zIndex,
                scaleX,
                scaleY
        );
        return validate(resolved);
    }

    private static VisualObject decode(
            Optional<ResourceLocation> asset,
            Optional<Map<String, ResourceLocation>> variants,
            String initialVariant,
            float x,
            float y,
            VisualAnchor anchor,
            float scale,
            Optional<VisualSampling> sampling,
            float opacity,
            boolean visible,
            int zIndex,
            float scaleX,
            float scaleY
    ) {
        Optional<VisualSampling> normalizedSampling = asset.isPresent()
                ? sampling
                : Optional.of(sampling.orElse(VisualSampling.LINEAR));
        return new VisualObject(
                asset,
                variants.orElse(Map.of()),
                initialVariant,
                x,
                y,
                anchor,
                scale,
                normalizedSampling,
                opacity,
                visible,
                zIndex,
                scaleX,
                scaleY
        );
    }

    private static DataResult<VisualObject> validate(VisualObject object) {
        float effectiveX = object.scale * object.scaleX, effectiveY = object.scale * object.scaleY;
        if (!Float.isFinite(object.scaleX) || !Float.isFinite(object.scaleY) || object.scaleX <= 0 || object.scaleY <= 0
                || !Float.isFinite(effectiveX) || !Float.isFinite(effectiveY) || effectiveX <= 0 || effectiveY <= 0) {
            return DataResult.error(() -> "VisualObject effective axis scales must be finite and greater than 0.");
        }
        boolean hasAsset = object.asset().isPresent();
        boolean hasVariants = !object.variants().isEmpty();
        if (hasAsset == hasVariants) {
            return DataResult.error(
                    () -> "VisualObject must define exactly one of asset or variants."
            );
        }
        if (!object.initialVariant().matches("[a-z0-9_-]+")) {
            return DataResult.error(
                    () -> "Invalid VisualObject initial_variant '"
                            + object.initialVariant() + "'."
            );
        }
        if (hasAsset) {
            return DataResult.success(object);
        }
        for (String variant : object.variants().keySet()) {
            if (!variant.matches("[a-z0-9_-]+")) {
                return DataResult.error(
                        () -> "Invalid VisualObject variant ID '"
                                + variant + "'."
                );
            }
        }
        if (!object.variants().containsKey(object.initialVariant())) {
            return DataResult.error(
                    () -> "VisualObject initial_variant '"
                            + object.initialVariant()
                            + "' is not present in variants."
            );
        }
        return DataResult.success(object);
    }
}
