package top.rookiestwo.maimai_dialogue.client.scene;

import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualAnchor;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualObject;

import java.util.Map;
import java.util.Objects;

public record SceneObjectState(
        Map<String, ResourceLocation> variants,
        String variant,
        float x,
        float y,
        VisualAnchor anchor,
        float scale,
        float opacity,
        boolean visible,
        int zIndex
) {
    public SceneObjectState {
        Objects.requireNonNull(variants, "variants");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(anchor, "anchor");
        variants = Map.copyOf(variants);
    }

    public static SceneObjectState initial(VisualObject object) {
        return new SceneObjectState(
                object.variants(),
                object.initialVariant(),
                object.x(),
                object.y(),
                object.anchor(),
                object.scale(),
                object.opacity(),
                object.visible(),
                object.zIndex()
        );
    }

    public ResourceLocation image() {
        return variants.get(variant);
    }

    public SceneObjectState withAnimated(
            float nextX,
            float nextY,
            float nextScale,
            float nextOpacity,
            String nextVariant,
            boolean nextVisible
    ) {
        return new SceneObjectState(
                variants,
                nextVariant,
                nextX,
                nextY,
                anchor,
                nextScale,
                nextOpacity,
                nextVisible,
                zIndex
        );
    }
}
