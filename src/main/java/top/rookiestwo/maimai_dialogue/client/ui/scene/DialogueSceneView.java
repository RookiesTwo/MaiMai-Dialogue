package top.rookiestwo.maimai_dialogue.client.ui.scene;

import top.rookiestwo.maimai_dialogue.client.ui.scene.SceneContentView.ObjectBinding;

import top.rookiestwo.maimai_dialogue.client.ui.animation.PlaybackTimeline;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ImageView;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.presentation.scene.BackgroundFit;
import top.rookiestwo.maimai_dialogue.presentation.Presentation;
import top.rookiestwo.maimai_dialogue.presentation.scene.SceneBackground;
import top.rookiestwo.maimai_dialogue.presentation.filter.SceneFilter;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualObject;
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

public final class DialogueSceneView extends FrameLayout {
    // VisualObject scale 以该屏幕高度为设计基准，其他分辨率自动等比缩放。
    private final Map<String, ObjectBinding> objectBindings =
            new LinkedHashMap<>();
    private final SceneImageRenderer imageRenderer = new SceneImageRenderer();
    private Presentation renderedPresentation;
    private SceneImageRenderer.ImageLayers backgroundLayers;
    private final SceneTransition transition = new SceneTransition(this);
    private final PlaybackTimeline sceneTimeline = new PlaybackTimeline();
    private long playbackToken = Long.MIN_VALUE;
    private float currentBackgroundOpacity;
    private Consumer<DialogueBoxState> dialogueBoxStateConsumer = ignored -> {
    };

    public DialogueSceneView(Context context) {
        super(context);
        setClickable(false);
    }

    public void setDialogueBoxStateConsumer(Consumer<DialogueBoxState> consumer) {
        dialogueBoxStateConsumer = consumer;
    }

    // 根据新的 Presentation 重建背景、对象和滤镜视图。
    public void apply(Presentation presentation) {
        cancelSceneAnimator();
        playbackToken = Long.MIN_VALUE;
        transition.finish();
        // Theme 或 DialogueBox 改变时复用相同 Scene，避免角色被重复淡入。
        if (transition.current() != null
                && sameSceneContent(renderedPresentation, presentation)) {
            renderedPresentation = presentation;
            return;
        }
        transition.begin(new SceneContentView(getContext()), currentBackgroundOpacity);
        objectBindings.clear();
        backgroundLayers = null;
        currentBackgroundOpacity = 0.0F;
        renderedPresentation = presentation;

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
        initializeDetachedScene(transition.current());
        transition.current().setAlpha(0.0F);
        addView(
                transition.current(),
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
    public void renderPlayback(
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
    public void clearScene() {
        cancelSceneAnimator();
        playbackToken = Long.MIN_VALUE;
        transition.releaseImages();
        removeAllViews();
        objectBindings.clear();
        transition.reset();
        renderedPresentation = null;
        backgroundLayers = null;
        currentBackgroundOpacity = 0.0F;
    }

    public void setPlaybackRate(float playbackRate) {
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
        transition.current().addObjectBinding(
                objectBindings.get(objectId)
        );
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
        if (transition.pending()) {
            transition.apply(
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
            boolean imageChanged = imageRenderer.apply(
                    binding.layers,
                    object.variants(),
                    object.image(),
                    object.opacity(),
                    ImageView.ScaleType.FIT_CENTER,
                    transitions.get(entry.getKey()),
                    binding.owner
            );
            if (imageChanged) {
                transition.current().measureObjectImages(binding);
            }
            int visibility = object.visible() ? VISIBLE : GONE;
            binding.layers.primary.setVisibility(visibility);
            if (!object.visible()) {
                binding.layers.underlay.setVisibility(INVISIBLE);
            }
            transition.current().applyObjectLayout(binding);
        }
        transition.current().requestLayout();
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

    // 完成场景切换并移除已经不可见的旧场景。

    private void addSceneView(View view, LayoutParams params) {
        transition.current().addView(view, params);
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
        SceneContentView scene = transition.current();
        for (int index = 0; index < scene.getChildCount(); index++) {
            if (scene.getChildAt(index) instanceof ImageView imageView) {
                imageView.setColorFilter(null);
            }
        }
    }

    // 释放当前和过渡场景持有的全部图片资源。

    private static ImageView.ScaleType scaleType(BackgroundFit fit) {
        return switch (fit) {
            case COVER -> ImageView.ScaleType.CENTER_CROP;
            case CONTAIN -> ImageView.ScaleType.FIT_CENTER;
            case STRETCH -> ImageView.ScaleType.FIT_XY;
        };
    }

    private static boolean sameSceneContent(
            Presentation current,
            Presentation next
    ) {
        return current != null
                && current.background().equals(next.background())
                && current.visualObjects().equals(next.visualObjects())
                && current.filter().equals(next.filter());
    }

}
