package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ImageView;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.dialogue.BackgroundFit;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;
import top.rookiestwo.maimai_dialogue.dialogue.SceneBackground;
import top.rookiestwo.maimai_dialogue.dialogue.SceneFilter;
import top.rookiestwo.maimai_dialogue.dialogue.VisualAnchor;
import top.rookiestwo.maimai_dialogue.dialogue.VisualObject;
import top.rookiestwo.maimai_dialogue.client.scene.SceneObjectState;
import top.rookiestwo.maimai_dialogue.client.scene.DialogueBoxState;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.client.scene.SceneState;
import top.rookiestwo.maimai_dialogue.client.scene.SceneBackgroundState;
import top.rookiestwo.maimai_dialogue.client.scene.VariantTransition;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

final class DialogueSceneView extends FrameLayout {
    private final Map<String, ObjectBinding> objectBindings =
            new LinkedHashMap<>();
    private final SceneImageRenderer imageRenderer = new SceneImageRenderer();
    private SceneContentView currentScene;
    private SceneContentView outgoingScene;
    private boolean sceneTransitionPending;
    private SceneImageRenderer.ImageLayers backgroundLayers;
    private final PlaybackTimeline sceneTimeline = new PlaybackTimeline();
    private long playbackToken = Long.MIN_VALUE;
    private float currentBackgroundOpacity;
    private float outgoingCoverageOpacity;
    private Consumer<DialogueBoxState> dialogueBoxStateConsumer = ignored -> {
    };

    DialogueSceneView(Context context) {
        super(context);
        setClickable(false);
    }

    void setDialogueBoxStateConsumer(Consumer<DialogueBoxState> consumer) {
        dialogueBoxStateConsumer = consumer;
    }

    // 根据新的 Presentation 重建背景、对象和滤镜视图。
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
        sceneTransitionPending = true;

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

    // 播放或跳过当前 ScenePlayback，并在阻塞动画结束时回调。
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

    // 取消动画并释放当前场景持有的全部视图和图片。
    void clearScene() {
        cancelSceneAnimator();
        playbackToken = Long.MIN_VALUE;
        releaseImages();
        removeAllViews();
        objectBindings.clear();
        currentScene = null;
        outgoingScene = null;
        sceneTransitionPending = false;
        backgroundLayers = null;
        currentBackgroundOpacity = 0.0F;
        outgoingCoverageOpacity = 0.0F;
    }

    void setPlaybackRate(float playbackRate) {
        sceneTimeline.setPlaybackRate(playbackRate);
    }

    private void addBackground(SceneBackground background) {
        ResourceLocation imageId = background.initialImage();
        Image image = imageRenderer.load(imageId, "background");
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
        backgroundLayers = new SceneImageRenderer.ImageLayers(
                primary,
                underlay,
                imageId
        );
    }

    private void addObject(String objectId, VisualObject object) {
        ResourceLocation imageId = object.initialImage();
        Image image = imageRenderer.load(imageId, "VisualObject " + objectId);
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
        imageRenderer.applySampling(primary, object.sampling());
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
                        new SceneImageRenderer.ImageLayers(
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

    private void startPlayback(
            ScenePlayback playback,
            Runnable blockingFinished
    ) {
        boolean[] blockingReported = {
                playback.blockingDurationMs() == 0
        };
        sceneTimeline.start(playback.totalDurationMs(), elapsed -> {
            applyPlaybackState(playback, elapsed);
            if (!blockingReported[0]
                    && elapsed >= playback.blockingDurationMs()) {
                blockingReported[0] = true;
                blockingFinished.run();
            }
        }, () -> {
            if (!blockingReported[0]) {
                blockingReported[0] = true;
                blockingFinished.run();
            }
        });
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
        if (sceneTransitionPending) {
            applySceneTransition(
                    playback.dialogueTransitionProgressAt(elapsedMs)
            );
        }
    }

    private void applyState(
            SceneState state,
            Map<String, VariantTransition> transitions
    ) {
        dialogueBoxStateConsumer.accept(state.dialogueBox());
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
            imageRenderer.apply(
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
        SceneImageRenderer.ImageLayers layers = backgroundLayers;
        if (layers == null) {
            return;
        }
        currentBackgroundOpacity = background.opacity();
        imageRenderer.apply(
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

    // 根据 Dialogue 透明度驱动新旧场景之间的交叉过渡。
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

    // 完成场景切换并移除已经不可见的旧场景。
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
        sceneTransitionPending = false;
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

    // 取消仍在运行的场景 Animator，避免旧回调修改新场景。
    private void cancelSceneAnimator() {
        sceneTimeline.cancel();
    }

    private void applyFilter(SceneFilter filter) {
        if (filter != null) {
            SceneFilterView filterView = new SceneFilterView(getContext());
            filterView.apply(filter);
            addSceneView(filterView, new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
            ));
        }
        SceneContentView scene = currentScene;
        for (int index = 0; index < scene.getChildCount(); index++) {
            if (scene.getChildAt(index) instanceof ImageView imageView) {
                imageView.setColorFilter(null);
            }
        }
    }

    // 释放当前和过渡场景持有的全部图片资源。
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

    private static final class ObjectBinding {
        private final SceneImageRenderer.ImageLayers layers;
        private final String owner;
        private SceneObjectState state;

        private ObjectBinding(
                SceneImageRenderer.ImageLayers layers,
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
            SceneImageRenderer.releaseImages(this);
        }
    }
}
