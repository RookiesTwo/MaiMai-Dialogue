package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.ImageView;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.client.scene.VariantTransition;
import top.rookiestwo.maimai_dialogue.dialogue.VisualSampling;

import java.util.Map;

@SuppressWarnings("deprecation")
final class SceneImageRenderer {
    // 加载资源图片，并统一报告缺失图片错误。
    Image load(ResourceLocation imageId, String owner) {
        Image image = Image.create(
                imageId.getNamespace(),
                imageId.getPath()
        );
        if (image == null) {
            ClientDialogueController.reportDevelopmentError(
                    "Client is missing image " + imageId + " for " + owner
            );
        }
        return image;
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
        Image image = load(imageId, owner);
        if (image != null) {
            layers.primary.setImage(image);
            applySampling(layers.primary, layers.sampling);
            layers.primaryId = imageId;
            return true;
        }
        return false;
    }

    private boolean setUnderlayImage(
            ImageLayers layers,
            ResourceLocation imageId,
            String owner
    ) {
        if (imageId.equals(layers.underlayId)) {
            return false;
        }
        Image image = load(imageId, owner);
        if (image != null) {
            layers.underlay.setImage(image);
            applySampling(layers.underlay, layers.sampling);
            layers.underlayId = imageId;
            layers.underlayImage = image;
            return true;
        }
        return false;
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
