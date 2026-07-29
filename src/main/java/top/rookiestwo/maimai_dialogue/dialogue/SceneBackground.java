package top.rookiestwo.maimai_dialogue.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record SceneBackground(
        ResourceLocation image,
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

    public static final Codec<SceneBackground> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            ResourceLocation.CODEC.fieldOf("image")
                                    .forGetter(SceneBackground::image),
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

    public SceneBackground {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(fit, "fit");
    }
}
