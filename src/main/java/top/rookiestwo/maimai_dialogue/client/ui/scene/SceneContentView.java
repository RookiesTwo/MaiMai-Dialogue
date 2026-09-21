package top.rookiestwo.maimai_dialogue.client.ui.scene;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import top.rookiestwo.maimai_dialogue.presentation.filter.SceneFilter;
import top.rookiestwo.maimai_dialogue.presentation.filter.CrtFilter;
import top.rookiestwo.maimai_dialogue.client.ui.scene.gpu.SceneColorLayer;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ImageView;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualAnchor;
import top.rookiestwo.maimai_dialogue.client.scene.SceneObjectState;

import java.util.LinkedHashMap;
import java.util.Map;

final class SceneContentView extends FrameLayout {
    private final DialogueImageSource images;
    private final Paint crtBackdrop = new Paint();
    private SceneFilter filter;
    private final long filterStartNanos = System.nanoTime();
    private SceneColorLayer colorLayer;
    private final Map<String, ObjectBinding> objectBindings =
            new LinkedHashMap<>();

    SceneContentView(Context context, DialogueImageSource images) {
        super(context);
        this.images = images;
        crtBackdrop.setColor(0xff000000);
        setClickable(false);
    }

    void addObjectBinding(ObjectBinding binding) {
        objectBindings.put(binding.owner, binding);
    }

    void setSceneFilter(SceneFilter value) {
        if (java.util.Objects.equals(filter, value)) return;
        filter = value;
        // Retain allocated surfaces while toggling neutral values; only uniforms change during adjustment.
        invalidate();
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        // Keep the CRT screen opaque within its own bounds, including warped corners and transparent artwork.
        // Draw outside the filter source so the backdrop stays black and follows the Scene's transform/alpha.
        if (filter instanceof CrtFilter) canvas.drawRect(0, 0, getWidth(), getHeight(), crtBackdrop);
        if (SceneColorLayer.isNeutral(filter)) { super.dispatchDraw(canvas); return; }
        if (colorLayer == null) colorLayer = new SceneColorLayer();
        float time = (float) (((System.nanoTime() - filterStartNanos) / 1_000_000_000.0) % 4096);
        colorLayer.draw(canvas, getWidth(), getHeight(), filter, time, super::dispatchDraw);
        if (filter instanceof CrtFilter crt && crt.animated()) postInvalidateOnAnimation();
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

    void applyObjectLayout(ObjectBinding binding) {
        // 异步图片到达后先消费其测量请求；直接 layout 会清除请求并沿用旧的零尺寸。
        if (binding.layers.primary.isLayoutRequested() || binding.layers.underlay.isLayoutRequested()) {
            measureObjectImages(binding);
        }
        applyObjectLayout(binding, getWidth(), getHeight());
    }

    // 与 Scene 切换一致，先准备新图片尺寸，再开始双层过渡。
    void measureObjectImages(ObjectBinding binding) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int widthSpec = MeasureSpec.makeMeasureSpec(
                width,
                MeasureSpec.AT_MOST
        );
        int heightSpec = MeasureSpec.makeMeasureSpec(
                height,
                MeasureSpec.AT_MOST
        );
        binding.layers.underlay.measure(widthSpec, heightSpec);
        binding.layers.primary.measure(widthSpec, heightSpec);
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

    void releaseImages() {
        if (colorLayer != null) { colorLayer.close(); colorLayer = null; }
        SceneImageRenderer.releaseImages(this);
        images.close();
    }

    @Override protected void onDetachedFromWindow() {
        if (colorLayer != null) { colorLayer.close(); colorLayer = null; }
        super.onDetachedFromWindow();
    }

    private static final float DESIGN_SCREEN_HEIGHT = 1080.0F;

    static final class ObjectBinding {
        final SceneImageRenderer.ImageLayers layers;
        final String owner;
        SceneObjectState state;

        ObjectBinding(
                SceneImageRenderer.ImageLayers layers,
                SceneObjectState state,
                String owner
        ) {
            this.layers = layers;
            this.state = state;
            this.owner = owner;
        }
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
        // ImageDrawable 会按 ModernUI density（随 guiScale 变化）自动缩放图片尺寸，
        // 这里除以 density 抵消，使 scale 在不同分辨率下占屏比例一致。
        float density = view.getContext()
                .getResources()
                .getDisplayMetrics()
                .density;
        float resolutionScale = height / DESIGN_SCREEN_HEIGHT / density;
        view.setScaleX(object.scale() * object.scaleX() * resolutionScale);
        view.setScaleY(object.scale() * object.scaleY() * resolutionScale);
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
}
