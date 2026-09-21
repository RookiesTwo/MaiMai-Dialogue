package top.rookiestwo.maimai_dialogue.presentation.scene;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.content.DefinitionCodecs;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualAssetDefinition;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualSampling;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Only the VisualAsset reference is serialized; resolved images belong to a content snapshot. */
public record SceneBackground(
        ResourceLocation asset,
        String initialVariant,
        BackgroundFit fit,
        float opacity,
        Optional<VisualAssetDefinition> resolvedAsset
) {
    private static final Codec<Float> OPACITY_CODEC = Codec.FLOAT.validate(
            value -> value >= 0.0F && value <= 1.0F
                    ? DataResult.success(value)
                    : DataResult.error(
                            () -> "Background opacity must be between 0 and 1."
                    )
    );

    private static final Codec<SceneBackground> BASE_CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            ResourceLocation.CODEC.fieldOf("asset")
                                    .forGetter(SceneBackground::asset),
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

    public static final Codec<SceneBackground> CODEC = DefinitionCodecs.rejectFields(BASE_CODEC.flatXmap(
            SceneBackground::validate,
            SceneBackground::validate
    ), "variants", "sampling");

    public SceneBackground(ResourceLocation asset, String initialVariant, BackgroundFit fit, float opacity) {
        this(asset, initialVariant, fit, opacity, Optional.empty());
    }

    public SceneBackground {
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(initialVariant, "initialVariant");
        Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(resolvedAsset, "resolvedAsset");
    }

    public Map<String, ResourceLocation> variants() {
        return resolvedAsset.map(VisualAssetDefinition::variants).orElse(Map.of());
    }

    public VisualSampling sampling() {
        return resolvedAsset.map(VisualAssetDefinition::sampling).orElse(VisualSampling.LINEAR);
    }

    public ResourceLocation initialImage() {
        return Objects.requireNonNull(variants().get(initialVariant), "Resolve Background VisualAsset before rendering");
    }

    public DataResult<SceneBackground> resolve(VisualAssetDefinition definition) {
        if (!definition.variants().containsKey(initialVariant)) {
            return DataResult.error(() -> "Background initial_variant '" + initialVariant
                    + "' is not present in VisualAsset " + asset + ".");
        }
        return DataResult.success(new SceneBackground(asset, initialVariant, fit, opacity, Optional.of(definition)));
    }

    private static DataResult<SceneBackground> validate(
            SceneBackground background
    ) {
        if (!background.initialVariant.matches("[a-z0-9_-]+")) {
            return DataResult.error(
                    () -> "Invalid Background initial_variant '" + background.initialVariant + "'."
            );
        }
        return DataResult.success(background);
    }
}
