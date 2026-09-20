package top.rookiestwo.maimai_dialogue.client.ui.scene;

import top.rookiestwo.maimai_dialogue.client.controller.ClientDiagnostics;

import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.ImageView;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.scene.VariantTransition;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualSampling;

import java.util.Map;

@SuppressWarnings("deprecation")
final class SceneImageRenderer {
    private final DialogueImageSource source;

    SceneImageRenderer(DialogueImageSource source) { this.source = source; }

    // 加载资源图片，并统一报告缺失图片错误。
    private void load(ResourceLocation imageId, String owner, java.util.function.Consumer<Image> ready) {
        source.load(imageId, image -> {
            if (image == null) ClientDiagnostics.report("Client is missing image " + imageId + " for " + owner);
            ready.accept(image);
        });
    }

    void initialize(ImageLayers layers, ResourceLocation imageId, String owner) {
        setPrimaryImage(layers, imageId, owner);
    }

    // 应用图片 variant；返回值用于让 WRAP_CONTENT 图层先完成测量。
    boolean apply(
            ImageLayers layers,
            Map<String, ResourceLocation> variants,
            ResourceLocation imageId,
            float opacity,
            ImageView.ScaleType scaleType,
            VariantTransition transition,
            String owner
    ) {
        layers.primary.setScaleType(scaleType);
        layers.underlay.setScaleType(scaleType);
        if (transition == null) {
            boolean imageChanged = promoteOrLoad(layers, imageId, owner);
            layers.primary.setImageAlpha(opacity);
            clearUnderlay(layers);
            return imageChanged;
        }

        ResourceLocation fromImage = variants.get(transition.fromVariant());
        ResourceLocation toImage = variants.get(transition.toVariant());
        boolean primaryChanged = setPrimaryImage(layers, fromImage, owner);
        boolean underlayChanged = setUnderlayImage(layers, toImage, owner);
        layers.primary.setImageAlpha(transition.outgoingAlpha(opacity));
        layers.underlay.setImageAlpha(transition.incomingAlpha(opacity));
        layers.underlay.setVisibility(View.VISIBLE);
        return primaryChanged || underlayChanged;
    }

    void applySampling(ImageView view, VisualSampling sampling) {
        if (view.getDrawable() instanceof ImageDrawable drawable) {
            drawable.setFilter(sampling == VisualSampling.LINEAR);
        }
    }

    // 解除场景子树对图片资源的引用。
    static void releaseImages(ViewGroup root) {
        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);
            if (child instanceof ImageView imageView) {
                imageView.setImage(null);
            } else if (child instanceof ViewGroup group) {
                releaseImages(group);
            }
        }
    }

    private boolean promoteOrLoad(
            ImageLayers layers,
            ResourceLocation imageId,
            String owner
    ) {
        if (imageId.equals(layers.underlayId)
                && layers.underlayImage != null) {
            boolean imageChanged = !imageId.equals(layers.primaryId);
            layers.primary.setImage(layers.underlayImage);
            applySampling(layers.primary, layers.sampling);
            layers.primaryId = layers.underlayId;
            return imageChanged;
        }
        return setPrimaryImage(layers, imageId, owner);
    }

    private boolean setPrimaryImage(
            ImageLayers layers,
            ResourceLocation imageId,
            String owner
    ) {
        if (imageId.equals(layers.primaryId)) {
            return false;
        }
        layers.primaryId = imageId;
        load(imageId, owner, image -> {
            if (image != null && imageId.equals(layers.primaryId) && layers.primary.getParent() != null) {
                layers.primary.setImage(image);
                applySampling(layers.primary, layers.sampling);
            }
        });
        return true;
    }

    private boolean setUnderlayImage(
            ImageLayers layers,
            ResourceLocation imageId,
            String owner
    ) {
        if (imageId.equals(layers.underlayId)) {
            return false;
        }
        layers.underlayId = imageId;
        layers.underlayImage = null;
        load(imageId, owner, image -> {
            if (image != null && imageId.equals(layers.underlayId) && layers.underlay.getParent() != null) {
                layers.underlay.setImage(image);
                applySampling(layers.underlay, layers.sampling);
                layers.underlayImage = image;
            }
        });
        return true;
    }

    private static void clearUnderlay(ImageLayers layers) {
        layers.underlay.setImage(null);
        layers.underlay.setVisibility(View.INVISIBLE);
        layers.underlayId = null;
        layers.underlayImage = null;
    }

    static final class ImageLayers {
        final ImageView primary;
        final ImageView underlay;
        final VisualSampling sampling;
        ResourceLocation primaryId;
        ResourceLocation underlayId;
        Image underlayImage;

        ImageLayers(
                ImageView primary,
                ImageView underlay,
                ResourceLocation primaryId
        ) {
            this(primary, underlay, primaryId, VisualSampling.LINEAR);
        }

        ImageLayers(
                ImageView primary,
                ImageView underlay,
                ResourceLocation primaryId,
                VisualSampling sampling
        ) {
            this.primary = primary;
            this.underlay = underlay;
            this.primaryId = primaryId;
            this.sampling = sampling;
        }
    }
}
