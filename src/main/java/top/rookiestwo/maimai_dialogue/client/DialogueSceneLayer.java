package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ImageView;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.dialogue.BackgroundFit;
import top.rookiestwo.maimai_dialogue.dialogue.ColorAdjustFilter;
import top.rookiestwo.maimai_dialogue.dialogue.CrtFilter;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;
import top.rookiestwo.maimai_dialogue.dialogue.SceneBackground;
import top.rookiestwo.maimai_dialogue.dialogue.SceneFilter;
import top.rookiestwo.maimai_dialogue.dialogue.VisualAnchor;
import top.rookiestwo.maimai_dialogue.dialogue.VisualObject;
import top.rookiestwo.maimai_dialogue.dialogue.VisualSampling;
import top.rookiestwo.maimai_dialogue.client.scene.SceneObjectState;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.client.scene.SceneState;
import top.rookiestwo.maimai_dialogue.client.scene.SceneBackgroundState;
import top.rookiestwo.maimai_dialogue.client.scene.VariantTransition;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
final class DialogueSceneLayer extends FrameLayout {
    private final Map<String, ObjectBinding> objectBindings =
            new LinkedHashMap<>();
    private SceneContentView currentScene;
    private SceneContentView outgoingScene;
    private ImageLayers backgroundLayers;
    private ValueAnimator sceneAnimator;
    private long playbackToken = Long.MIN_VALUE;
    private float currentBackgroundOpacity;
    private float outgoingCoverageOpacity;
    private Consumer<Float> dialogueOpacityConsumer = ignored -> {
    };

    DialogueSceneLayer(Context context) {
        super(context);
        setClickable(false);
    }

    void setDialogueOpacityConsumer(Consumer<Float> consumer) {
        dialogueOpacityConsumer = consumer;
    }

    void apply(Presentation presentation) {
        cancelSceneAnimator();
        playbackToken = Long.MIN_VALUE;
        finishSceneTransition();
        outgoingScene = currentScene;
        outgoingCoverageOpacity = currentBackgroundOpacity;
        objectBindings.clear();
        backgroundLayers = null;
        currentBackgroundOpacity = 0.0F;
        currentScene = new SceneContentView(getContext());

        presentation.background().ifPresent(this::addBackground);
        presentation.visualObjects().entrySet().stream()
                .sorted(Comparator
                        .comparingInt(
                                (Map.Entry<String, VisualObject> entry) ->
                                        entry.getValue().zIndex()
                        )
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> addObject(
                        entry.getKey(),
                        entry.getValue()
                ));
        applyFilter(presentation.filter().orElse(null));
        initializeDetachedScene(currentScene);
        currentScene.setAlpha(0.0F);
        addView(
                currentScene,
                0,
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );
        requestLayout();
        invalidate();
    }

    void renderPlayback(
            ScenePlayback playback,
            boolean skip,
            Runnable blockingFinished
    ) {
        if (playback.token() != playbackToken) {
            cancelSceneAnimator();
            playbackToken = playback.token();
            applyPlaybackState(playback, 0);
            if (skip || playback.totalDurationMs() == 0) {
                applyPlaybackState(playback, playback.totalDurationMs());
                return;
            }
            startPlayback(playback, blockingFinished);
            return;
        }
        if (skip) {
            cancelSceneAnimator();
            applyPlaybackState(playback, playback.totalDurationMs());
        }
    }

    void clearScene() {
        cancelSceneAnimator();
        playbackToken = Long.MIN_VALUE;
        releaseImages();
        removeAllViews();
        objectBindings.clear();
        currentScene = null;
        outgoingScene = null;
        backgroundLayers = null;
        currentBackgroundOpacity = 0.0F;
        outgoingCoverageOpacity = 0.0F;
    }

    private void addBackground(SceneBackground background) {
        ResourceLocation imageId = background.initialImage();
        Image image = loadImage(imageId, "background");
        if (image == null) {
            return;
        }

        ImageView underlay = new ImageView(getContext());
        underlay.setScaleType(scaleType(background.fit()));
        underlay.setVisibility(INVISIBLE);
        addSceneView(
                underlay,
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );

        ImageView primary = new ImageView(getContext());
        primary.setImage(image);
        primary.setImageAlpha(background.opacity());
        primary.setScaleType(scaleType(background.fit()));
        addSceneView(
                primary,
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );
        backgroundLayers = new ImageLayers(primary, underlay, imageId);
    }

    private void addObject(String objectId, VisualObject object) {
        ResourceLocation imageId = object.initialImage();
        Image image = loadImage(imageId, "VisualObject " + objectId);
        if (image == null) {
            return;
        }

        ImageView underlay = new ImageView(getContext());
        underlay.setScaleType(ImageView.ScaleType.FIT_CENTER);
        underlay.setVisibility(INVISIBLE);
        addSceneView(
                underlay,
                new LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                )
        );

        ImageView primary = new ImageView(getContext());
        primary.setImage(image);
        applySampling(primary, object.sampling());
        primary.setImageAlpha(object.opacity());
        primary.setScaleType(ImageView.ScaleType.FIT_CENTER);
        primary.setVisibility(object.visible() ? VISIBLE : GONE);
        addSceneView(
                primary,
                new LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                )
        );
        objectBindings.put(
                objectId,
                new ObjectBinding(
                        new ImageLayers(
                                primary,
                                underlay,
                                imageId,
                                object.sampling()
                        ),
                        SceneObjectState.initial(object),
                        "VisualObject " + objectId
                )
        );
        currentScene.addObjectBinding(
                objectBindings.get(objectId)
        );
    }

    private static void layoutObjectView(
            ImageView view,
            SceneObjectState object,
            int width,
            int height
    ) {
        int childWidth = view.getMeasuredWidth();
        int childHeight = view.getMeasuredHeight();
        float anchorX = horizontalAnchor(object.anchor());
        float anchorY = verticalAnchor(object.anchor());

        int childLeft = Math.round(
                object.x() * width - anchorX * childWidth
        );
        int childTop = Math.round(
                object.y() * height - anchorY * childHeight
        );
        view.layout(
                childLeft,
                childTop,
                childLeft + childWidth,
                childTop + childHeight
        );
        view.setPivotX(anchorX * childWidth);
        view.setPivotY(anchorY * childHeight);
        view.setScaleX(object.scale());
        view.setScaleY(object.scale());
    }

    private Image loadImage(ResourceLocation imageId, String owner) {
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

    private void startPlayback(
            ScenePlayback playback,
            Runnable blockingFinished
    ) {
        ValueAnimator animator = ValueAnimator.ofFloat(0.0F, 1.0F);
        animator.setDuration(playback.totalDurationMs());
        boolean[] blockingReported = {
                playback.blockingDurationMs() == 0
        };
        animator.addUpdateListener(valueAnimator -> {
            int elapsed = Math.round(
                    valueAnimator.getAnimatedFraction()
                            * playback.totalDurationMs()
            );
            applyPlaybackState(playback, elapsed);
            if (!blockingReported[0]
                    && elapsed >= playback.blockingDurationMs()) {
                blockingReported[0] = true;
                blockingFinished.run();
            }
        });
        sceneAnimator = animator;
        animator.start();
    }

    private void applyPlaybackState(
            ScenePlayback playback,
            int elapsedMs
    ) {
        SceneState state = playback.stateAt(elapsedMs);
        applyState(
                state,
                playback.variantTransitionsAt(elapsedMs)
        );
        applySceneTransition(
                playback.dialogueTransitionProgressAt(elapsedMs)
        );
    }

    private void applyState(
            SceneState state,
            Map<String, VariantTransition> transitions
    ) {
        dialogueOpacityConsumer.accept(state.dialogueOpacity());
        state.background().ifPresent(background ->
                applyBackgroundState(
                        background,
                        transitions.get("background")
                )
        );
        for (Map.Entry<String, SceneObjectState> entry :
                state.objects().entrySet()) {
            ObjectBinding binding = objectBindings.get(entry.getKey());
            if (binding == null) {
                continue;
            }
            SceneObjectState object = entry.getValue();
            binding.state = object;
            applyImageState(
                    binding.layers,
                    object.variants(),
                    object.image(),
                    object.opacity(),
                    ImageView.ScaleType.FIT_CENTER,
                    transitions.get(entry.getKey()),
                    binding.owner
            );
            int visibility = object.visible() ? VISIBLE : GONE;
            binding.layers.primary.setVisibility(visibility);
            if (!object.visible()) {
                binding.layers.underlay.setVisibility(INVISIBLE);
            }
            currentScene.applyObjectLayout(binding);
        }
        currentScene.requestLayout();
        invalidate();
    }

    private void applyBackgroundState(
            SceneBackgroundState background,
            VariantTransition transition
    ) {
        ImageLayers layers = backgroundLayers;
        if (layers == null) {
            return;
        }
        currentBackgroundOpacity = background.opacity();
        applyImageState(
                layers,
                background.variants(),
                background.image(),
                background.opacity(),
                scaleType(background.fit()),
                transition,
                "background"
        );
        layers.primary.setVisibility(VISIBLE);
    }

    private void applyImageState(
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
            promoteOrLoad(layers, imageId, owner);
            layers.primary.setImageAlpha(opacity);
            clearUnderlay(layers);
            return;
        }

        ResourceLocation fromImage =
                variants.get(transition.fromVariant());
        ResourceLocation toImage =
                variants.get(transition.toVariant());
        setPrimaryImage(layers, fromImage, owner);
        setUnderlayImage(layers, toImage, owner);

        layers.primary.setImageAlpha(transition.outgoingAlpha(opacity));
        layers.underlay.setImageAlpha(transition.incomingAlpha(opacity));
        layers.underlay.setVisibility(VISIBLE);
    }

    private void promoteOrLoad(
            ImageLayers layers,
            ResourceLocation imageId,
            String owner
    ) {
        if (imageId.equals(layers.underlayId)
                && layers.underlayImage != null) {
            layers.primary.setImage(layers.underlayImage);
            applySampling(layers.primary, layers.sampling);
            layers.primaryId = layers.underlayId;
            return;
        }
        setPrimaryImage(layers, imageId, owner);
    }

    private void setPrimaryImage(
            ImageLayers layers,
            ResourceLocation imageId,
            String owner
    ) {
        if (imageId.equals(layers.primaryId)) {
            return;
        }
        Image image = loadImage(imageId, owner);
        if (image != null) {
            layers.primary.setImage(image);
            applySampling(layers.primary, layers.sampling);
            layers.primaryId = imageId;
        }
    }

    private void setUnderlayImage(
            ImageLayers layers,
            ResourceLocation imageId,
            String owner
    ) {
        if (imageId.equals(layers.underlayId)) {
            return;
        }
        Image image = loadImage(imageId, owner);
        if (image != null) {
            layers.underlay.setImage(image);
            applySampling(layers.underlay, layers.sampling);
            layers.underlayId = imageId;
            layers.underlayImage = image;
        }
    }

    private static void clearUnderlay(ImageLayers layers) {
        layers.underlay.setImage(null);
        layers.underlay.setVisibility(INVISIBLE);
        layers.underlayId = null;
        layers.underlayImage = null;
    }

    private void applySceneTransition(float progress) {
        SceneContentView incoming = currentScene;
        SceneContentView outgoing = outgoingScene;
        if (incoming == null && outgoing == null) {
            return;
        }
        float clamped = Math.clamp(progress, 0.0F, 1.0F);
        float outgoingFactor = 1.0F - clamped;
        float outgoingAlpha =
                outgoingCoverageOpacity * outgoingFactor;
        float denominator = 1.0F - outgoingAlpha;
        float incomingFactor = denominator <= 0.0001F
                ? 0.0F
                : Math.clamp(clamped / denominator, 0.0F, 1.0F);

        if (outgoing != null) {
            outgoing.setAlpha(outgoingFactor);
        }
        if (incoming != null) {
            incoming.setAlpha(incomingFactor);
        }

        if (clamped >= 0.9999F) {
            finishSceneTransition();
        }
    }

    private void finishSceneTransition() {
        if (outgoingScene != null) {
            outgoingScene.releaseImages();
            removeView(outgoingScene);
            outgoingScene = null;
        }
        if (currentScene != null) {
            currentScene.setAlpha(1.0F);
        }
        outgoingCoverageOpacity = 0.0F;
    }

    private void addSceneView(View view, LayoutParams params) {
        currentScene.addView(view, params);
    }

    private void initializeDetachedScene(SceneContentView scene) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        scene.measure(
                MeasureSpec.makeMeasureSpec(
                        width,
                        MeasureSpec.EXACTLY
                ),
                MeasureSpec.makeMeasureSpec(
                        height,
                        MeasureSpec.EXACTLY
                )
        );
        scene.layout(0, 0, width, height);
    }

    private void cancelSceneAnimator() {
        if (sceneAnimator != null) {
            sceneAnimator.cancel();
            sceneAnimator = null;
        }
    }

    private void applyFilter(SceneFilter filter) {
        if (filter instanceof ColorAdjustFilter colorAdjust) {
            addColorAdjustOverlays(colorAdjust);
        } else if (filter instanceof CrtFilter crt) {
            addSceneView(
                    new CrtOverlayView(getContext(), crt),
                    new LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT
                    )
            );
        }
        SceneContentView scene = currentScene;
        for (int index = 0; index < scene.getChildCount(); index++) {
            if (scene.getChildAt(index) instanceof ImageView imageView) {
                imageView.setColorFilter(null);
            }
        }
    }

    private void addColorAdjustOverlays(ColorAdjustFilter filter) {
        float neutralWash = Math.max(0.0F, 1.0F - filter.saturation())
                * 0.18F;
        neutralWash += Math.max(0.0F, 1.0F - filter.contrast())
                * 0.18F;
        addColorOverlay(0xFF808080, neutralWash);

        filter.tint().ifPresent(tint -> {
            int argb = tint.argb();
            addColorOverlay(
                    argb | 0xFF000000,
                    ((argb >>> 24) & 0xFF) / 255.0F
            );
        });

        float brightness = filter.brightness();
        if (brightness < 0.0F) {
            addColorOverlay(0xFF000000, -brightness);
        } else {
            addColorOverlay(0xFFFFFFFF, brightness);
        }

        if (filter.contrast() > 1.0F) {
            addColorOverlay(
                    0xFF000000,
                    (filter.contrast() - 1.0F) * 0.08F
            );
        }
    }

    private void addColorOverlay(int rgb, float alpha) {
        int alphaByte = Math.round(Math.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        if (alphaByte == 0) {
            return;
        }
        View overlay = new View(getContext());
        overlay.setBackground(new ColorDrawable(
                (alphaByte << 24) | (rgb & 0x00FFFFFF)
        ));
        overlay.setClickable(false);
        addSceneView(
                overlay,
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );
    }

    private void releaseImages() {
        for (int index = 0; index < getChildCount(); index++) {
            if (getChildAt(index) instanceof SceneContentView scene) {
                scene.releaseImages();
            }
        }
    }

    private static ImageView.ScaleType scaleType(BackgroundFit fit) {
        return switch (fit) {
            case COVER -> ImageView.ScaleType.CENTER_CROP;
            case CONTAIN -> ImageView.ScaleType.FIT_CENTER;
            case STRETCH -> ImageView.ScaleType.FIT_XY;
        };
    }

    private static float horizontalAnchor(VisualAnchor anchor) {
        return switch (anchor) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> 0.0F;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> 0.5F;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> 1.0F;
        };
    }

    private static float verticalAnchor(VisualAnchor anchor) {
        return switch (anchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0.0F;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> 0.5F;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> 1.0F;
        };
    }

    private static void applySampling(
            ImageView view,
            VisualSampling sampling
    ) {
        if (view.getDrawable() instanceof ImageDrawable drawable) {
            drawable.setFilter(sampling == VisualSampling.LINEAR);
        }
    }

    private static final class ObjectBinding {
        private final ImageLayers layers;
        private final String owner;
        private SceneObjectState state;

        private ObjectBinding(
                ImageLayers layers,
                SceneObjectState state,
                String owner
        ) {
            this.layers = layers;
            this.state = state;
            this.owner = owner;
        }
    }

    private static final class SceneContentView extends FrameLayout {
        private final Map<String, ObjectBinding> objectBindings =
                new LinkedHashMap<>();

        private SceneContentView(Context context) {
            super(context);
            setClickable(false);
        }

        private void addObjectBinding(ObjectBinding binding) {
            objectBindings.put(binding.owner, binding);
        }

        @Override
        protected void onLayout(
                boolean changed,
                int left,
                int top,
                int right,
                int bottom
        ) {
            super.onLayout(changed, left, top, right, bottom);
            int width = right - left;
            int height = bottom - top;

            for (ObjectBinding binding : objectBindings.values()) {
                applyObjectLayout(binding, width, height);
            }
        }

        private void applyObjectLayout(ObjectBinding binding) {
            applyObjectLayout(binding, getWidth(), getHeight());
        }

        private static void applyObjectLayout(
                ObjectBinding binding,
                int width,
                int height
        ) {
            if (width <= 0
                    || height <= 0
                    || (binding.layers.primary.getVisibility() == GONE
                    && binding.layers.underlay.getVisibility()
                    != VISIBLE)) {
                return;
            }
            layoutObjectView(
                    binding.layers.underlay,
                    binding.state,
                    width,
                    height
            );
            layoutObjectView(
                    binding.layers.primary,
                    binding.state,
                    width,
                    height
            );
        }

        private void releaseImages() {
            for (int index = 0; index < getChildCount(); index++) {
                if (getChildAt(index) instanceof ImageView imageView) {
                    imageView.setImage(null);
                }
            }
        }
    }

    private static final class ImageLayers {
        private final ImageView primary;
        private final ImageView underlay;
        private final VisualSampling sampling;
        private ResourceLocation primaryId;
        private ResourceLocation underlayId;
        private Image underlayImage;

        private ImageLayers(
                ImageView primary,
                ImageView underlay,
                ResourceLocation primaryId
        ) {
            this(
                    primary,
                    underlay,
                    primaryId,
                    VisualSampling.LINEAR
            );
        }

        private ImageLayers(
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
