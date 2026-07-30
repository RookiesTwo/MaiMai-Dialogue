package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ColorDrawable;
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
import top.rookiestwo.maimai_dialogue.client.scene.BackgroundCrossfade;
import top.rookiestwo.maimai_dialogue.client.scene.SceneObjectState;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.client.scene.SceneState;
import top.rookiestwo.maimai_dialogue.client.scene.SceneBackgroundState;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
final class DialogueSceneLayer extends FrameLayout {
    private final Map<String, ObjectBinding> objectBindings =
            new LinkedHashMap<>();
    private ImageView backgroundView;
    private ResourceLocation backgroundImageId;
    private ImageView backgroundFadeView;
    private ResourceLocation backgroundFadeImageId;
    private ValueAnimator sceneAnimator;
    private long playbackToken = Long.MIN_VALUE;
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
        releaseImages();
        removeAllViews();
        objectBindings.clear();
        backgroundView = null;
        backgroundImageId = null;
        backgroundFadeView = null;
        backgroundFadeImageId = null;

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
        backgroundView = null;
        backgroundImageId = null;
        backgroundFadeView = null;
        backgroundFadeImageId = null;
    }

    private void addBackground(SceneBackground background) {
        ResourceLocation imageId = background.initialImage();
        Image image = loadImage(imageId, "background");
        if (image == null) {
            return;
        }

        ImageView imageView = new ImageView(getContext());
        imageView.setImage(image);
        imageView.setImageAlpha(background.opacity());
        imageView.setScaleType(scaleType(background.fit()));
        addView(
                imageView,
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );
        backgroundView = imageView;
        backgroundImageId = imageId;

        ImageView fadeView = new ImageView(getContext());
        fadeView.setScaleType(scaleType(background.fit()));
        fadeView.setVisibility(GONE);
        addView(
                fadeView,
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );
        backgroundFadeView = fadeView;
    }

    private void addObject(String objectId, VisualObject object) {
        ResourceLocation imageId = object.initialImage();
        Image image = loadImage(imageId, "VisualObject " + objectId);
        if (image == null) {
            return;
        }

        ImageView imageView = new ImageView(getContext());
        imageView.setImage(image);
        imageView.setImageAlpha(object.opacity());
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setVisibility(object.visible() ? VISIBLE : GONE);
        addView(
                imageView,
                new LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                )
        );
        objectBindings.put(
                objectId,
                new ObjectBinding(
                        imageView,
                        SceneObjectState.initial(object),
                        imageId
                )
        );
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
            ImageView view = binding.view;
            if (view.getVisibility() == GONE) {
                continue;
            }

            SceneObjectState object = binding.state;
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
        applyState(
                playback.stateAt(elapsedMs),
                playback.backgroundCrossfadeAt(elapsedMs)
        );
    }

    private void applyState(
            SceneState state,
            Optional<BackgroundCrossfade> backgroundCrossfade
    ) {
        dialogueOpacityConsumer.accept(state.dialogueOpacity());
        state.background().ifPresent(background ->
                applyBackgroundState(background, backgroundCrossfade)
        );
        for (Map.Entry<String, SceneObjectState> entry :
                state.objects().entrySet()) {
            ObjectBinding binding = objectBindings.get(entry.getKey());
            if (binding == null) {
                continue;
            }
            SceneObjectState object = entry.getValue();
            if (!object.image().equals(binding.imageId)) {
                Image image = loadImage(
                        object.image(),
                        "VisualObject " + entry.getKey()
                );
                if (image != null) {
                    binding.view.setImage(image);
                    binding.imageId = object.image();
                }
            }
            binding.state = object;
            binding.view.setImageAlpha(object.opacity());
            binding.view.setVisibility(object.visible() ? VISIBLE : GONE);
        }
        requestLayout();
        invalidate();
    }

    private void applyBackgroundState(
            SceneBackgroundState background,
            Optional<BackgroundCrossfade> crossfade
    ) {
        ImageView view = backgroundView;
        ImageView fadeView = backgroundFadeView;
        if (view == null || fadeView == null) {
            return;
        }
        if (crossfade.isPresent()) {
            BackgroundCrossfade transition = crossfade.orElseThrow();
            applyBackgroundImage(
                    view,
                    transition.from().image(),
                    false
            );
            applyBackgroundImage(
                    fadeView,
                    transition.to().image(),
                    true
            );
            float progress = transition.progress();
            view.setImageAlpha(
                    transition.from().opacity() * (1.0F - progress)
            );
            fadeView.setImageAlpha(
                    transition.to().opacity() * progress
            );
            view.setScaleType(scaleType(transition.from().fit()));
            fadeView.setScaleType(scaleType(transition.to().fit()));
            fadeView.setVisibility(VISIBLE);
            return;
        }

        ResourceLocation imageId = background.image();
        if (fadeView.getVisibility() == VISIBLE
                && imageId.equals(backgroundFadeImageId)) {
            ImageView previous = view;
            backgroundView = fadeView;
            backgroundFadeView = previous;
            backgroundImageId = backgroundFadeImageId;
            backgroundFadeImageId = null;
            previous.setImage(null);
            previous.setVisibility(GONE);
            view = backgroundView;
        }
        if (!imageId.equals(backgroundImageId)) {
            applyBackgroundImage(view, imageId, false);
        }
        view.setImageAlpha(background.opacity());
        view.setScaleType(scaleType(background.fit()));
        view.setVisibility(VISIBLE);
        hideBackgroundFade();
    }

    private void applyBackgroundImage(
            ImageView view,
            ResourceLocation imageId,
            boolean fade
    ) {
        ResourceLocation current = fade
                ? backgroundFadeImageId
                : backgroundImageId;
        if (imageId.equals(current)) {
            return;
        }
        Image image = loadImage(imageId, "background");
        if (image == null) {
            return;
        }
        view.setImage(image);
        if (fade) {
            backgroundFadeImageId = imageId;
        } else {
            backgroundImageId = imageId;
        }
    }

    private void hideBackgroundFade() {
        ImageView fadeView = backgroundFadeView;
        if (fadeView == null) {
            return;
        }
        fadeView.setImage(null);
        fadeView.setVisibility(GONE);
        backgroundFadeImageId = null;
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
            addView(
                    new CrtOverlayView(getContext(), crt),
                    new LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT
                    )
            );
        }
        for (int index = 0; index < getChildCount(); index++) {
            if (getChildAt(index) instanceof ImageView imageView) {
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
        addView(
                overlay,
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );
    }

    private void releaseImages() {
        for (int index = 0; index < getChildCount(); index++) {
            if (getChildAt(index) instanceof ImageView imageView) {
                imageView.setImage(null);
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

    private static final class ObjectBinding {
        private final ImageView view;
        private SceneObjectState state;
        private ResourceLocation imageId;

        private ObjectBinding(
                ImageView view,
                SceneObjectState state,
                ResourceLocation imageId
        ) {
            this.view = view;
            this.state = state;
            this.imageId = imageId;
        }
    }
}
