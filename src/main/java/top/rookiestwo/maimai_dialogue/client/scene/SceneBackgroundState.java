package top.rookiestwo.maimai_dialogue.client.scene;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.dialogue.BackgroundFit;
import top.rookiestwo.maimai_dialogue.dialogue.SceneBackground;

import java.util.Map;
import java.util.Objects;

public record SceneBackgroundState(
        Map<String, ResourceLocation> variants,
        String variant,
        BackgroundFit fit,
        float opacity
) {
    public SceneBackgroundState {
        Objects.requireNonNull(variants, "variants");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(fit, "fit");
        variants = Map.copyOf(variants);
    }

    public static SceneBackgroundState initial(SceneBackground background) {
        return new SceneBackgroundState(
                background.variants(),
                background.initialVariant(),
                background.fit(),
                background.opacity()
        );
    }

    public ResourceLocation image() {
        return variants.get(variant);
    }

    public SceneBackgroundState withVariant(String nextVariant) {
        return new SceneBackgroundState(
                variants,
                nextVariant,
                fit,
                opacity
        );
    }
}
