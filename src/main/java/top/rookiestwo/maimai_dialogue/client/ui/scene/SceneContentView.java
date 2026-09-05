package top.rookiestwo.maimai_dialogue.client.ui.scene;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ImageView;
import top.rookiestwo.maimai_dialogue.presentation.visual.VisualAnchor;
import top.rookiestwo.maimai_dialogue.client.scene.SceneObjectState;

import java.util.LinkedHashMap;
import java.util.Map;

final class SceneContentView extends FrameLayout {
    private final Map<String, ObjectBinding> objectBindings =
            new LinkedHashMap<>();

    SceneContentView(Context context) {
        super(context);
        setClickable(false);
    }

    void addObjectBinding(ObjectBinding binding) {
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

    void applyObjectLayout(ObjectBinding binding) {
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
        SceneImageRenderer.releaseImages(this);
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
        view.setScaleX(object.scale() * resolutionScale);
        view.setScaleY(object.scale() * resolutionScale);
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
