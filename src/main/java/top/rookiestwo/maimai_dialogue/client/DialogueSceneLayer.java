package top.rookiestwo.maimai_dialogue.client;

import icyllis.arc3d.sketch.Surface;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ImageView;
import net.minecraft.resources.ResourceLocation;
import top.rookiestwo.maimai_dialogue.MaiMaiDialogue;
import top.rookiestwo.maimai_dialogue.dialogue.BackgroundFit;
import top.rookiestwo.maimai_dialogue.dialogue.ColorAdjustFilter;
import top.rookiestwo.maimai_dialogue.dialogue.CrtFilter;
import top.rookiestwo.maimai_dialogue.dialogue.Presentation;
import top.rookiestwo.maimai_dialogue.dialogue.SceneBackground;
import top.rookiestwo.maimai_dialogue.dialogue.SceneFilter;
import top.rookiestwo.maimai_dialogue.dialogue.VisualAnchor;
import top.rookiestwo.maimai_dialogue.dialogue.VisualObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
final class DialogueSceneLayer extends FrameLayout {
    private final List<ObjectBinding> objectBindings = new ArrayList<>();
    private SceneCompositeEffect compositeEffect;
    private Surface renderTarget;
    private ArcCanvas renderTargetCanvas;
    private int renderTargetWidth;
    private int renderTargetHeight;
    private boolean warnedAboutCrt;

    DialogueSceneLayer(Context context) {
        super(context);
        setClickable(false);
    }

    void apply(Presentation presentation) {
        releaseImages();
        removeAllViews();
        objectBindings.clear();

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

    void clearScene() {
        releaseRenderTarget();
        releaseCompositeEffect();
        releaseImages();
        removeAllViews();
        objectBindings.clear();
        warnedAboutCrt = false;
    }

    private void addBackground(SceneBackground background) {
        Image image = loadImage(background.image(), "background");
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
        objectBindings.add(new ObjectBinding(imageView, object));
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

        for (ObjectBinding binding : objectBindings) {
            ImageView view = binding.view();
            if (view.getVisibility() == GONE) {
                continue;
            }

            VisualObject object = binding.definition();
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

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        SceneCompositeEffect effect = compositeEffect;
        if (effect == null || !(canvas instanceof ArcCanvas arcCanvas)) {
            super.dispatchDraw(canvas);
            return;
        }

        int width = getWidth();
        int height = getHeight();
        if (width <= 0
                || height <= 0
                || !ensureRenderTarget(arcCanvas, width, height)) {
            super.dispatchDraw(canvas);
            return;
        }

        icyllis.arc3d.sketch.Canvas offscreen =
                renderTarget.getCanvas();
        offscreen.clear(0.0F, 0.0F, 0.0F, 0.0F);
        super.dispatchDraw(renderTargetCanvas);

        icyllis.arc3d.sketch.Image scene =
                renderTarget.makeImageSnapshot();
        if (scene == null) {
            super.dispatchDraw(canvas);
            return;
        }
        try {
            effect.draw(arcCanvas.getCanvas(), scene, width, height);
        } finally {
            scene.unref();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseRenderTarget();
        super.onDetachedFromWindow();
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

    private void applyFilter(SceneFilter filter) {
        releaseRenderTarget();
        releaseCompositeEffect();
        if (filter instanceof ColorAdjustFilter colorAdjust) {
            compositeEffect = new SceneCompositeEffect.ColorAdjust(
                    colorAdjust
            );
        } else if (filter instanceof CrtFilter && !warnedAboutCrt) {
            warnedAboutCrt = true;
            MaiMaiDialogue.LOGGER.warn(
                    "CRT scene filter data was loaded, but its shader "
                            + "renderer is not implemented yet."
            );
        }
    }

    private boolean ensureRenderTarget(
            ArcCanvas destination,
            int width,
            int height
    ) {
        if (renderTarget != null
                && renderTargetWidth == width
                && renderTargetHeight == height) {
            return true;
        }

        releaseRenderTarget();
        icyllis.arc3d.sketch.Canvas destinationCanvas =
                destination.getCanvas();
        renderTarget = destinationCanvas.makeSurface(
                destinationCanvas.getImageInfo().makeWH(width, height)
        );
        if (renderTarget == null) {
            MaiMaiDialogue.LOGGER.warn(
                    "Could not create the Dialogue Scene RenderTarget; "
                            + "rendering the scene without its filter."
            );
            return false;
        }

        renderTargetWidth = width;
        renderTargetHeight = height;
        renderTargetCanvas = new ArcCanvas(renderTarget.getCanvas());
        return true;
    }

    private void releaseRenderTarget() {
        if (renderTarget != null) {
            renderTarget.unref();
            renderTarget = null;
        }
        renderTargetCanvas = null;
        renderTargetWidth = 0;
        renderTargetHeight = 0;
    }

    private void releaseCompositeEffect() {
        if (compositeEffect != null) {
            compositeEffect.close();
            compositeEffect = null;
        }
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

    private record ObjectBinding(
            ImageView view,
            VisualObject definition
    ) {
    }
}
