package top.rookiestwo.maimai_dialogue.client.ui.layout;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ImageView;

// 只刷新本 MOD 的 View 树；窗口回调来自客户端线程，尺寸应用留在 UI 的测量阶段。
public abstract class ResponsiveFrameLayout extends FrameLayout {
    private int viewportWidth = -1;
    private int viewportHeight = -1;
    private float density;
    private float scaledDensity;
    private MuiModApi.OnWindowResizeListener resizeListener;
    private long attachment;
    private java.util.List<ScrollPosition> scrollPositions = java.util.List.of();

    protected ResponsiveFrameLayout(Context context) {
        super(context);
        var metrics = context.getResources().getDisplayMetrics();
        density = metrics.density;
        scaledDensity = metrics.scaledDensity;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        long expectedAttachment = ++attachment;
        MuiModApi.OnWindowResizeListener listener = (width, height, scale, oldScale) -> post(() -> {
            // 已销毁或重新挂载的 View 不接收旧回调。
            if (isAttachedToWindow() && attachment == expectedAttachment) {
                requestLayout();
            }
        });
        resizeListener = listener;
        MuiModApi.addOnWindowResizeListener(listener);
        requestLayout();
    }

    @Override
    protected void onDetachedFromWindow() {
        MuiModApi.removeOnWindowResizeListener(resizeListener);
        resizeListener = null;
        attachment++;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        prepareViewport(widthMeasureSpec, heightMeasureSpec);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    protected final void prepareViewport(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        var metrics = getContext().getResources().getDisplayMetrics();
        boolean densityChanged = density != metrics.density || scaledDensity != metrics.scaledDensity;
        if (width != viewportWidth || height != viewportHeight || densityChanged) {
            scrollPositions = ScrollPosition.capture(this);
            viewportWidth = width;
            viewportHeight = height;
            density = metrics.density;
            scaledDensity = metrics.scaledDensity;
            onViewportChanged(densityChanged);
            refreshChildren(this, densityChanged, metrics.densityDpi);
        }
    }

    protected abstract void onViewportChanged(boolean densityChanged);

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        restoreScrollPositions();
    }

    protected final void restoreScrollPositions() {
        scrollPositions.forEach(ScrollPosition::restore);
        scrollPositions = java.util.List.of();
    }

    private static void refreshChildren(View view, boolean densityChanged, int densityDpi) {
        if (densityChanged && view instanceof ImageView image
                && image.getDrawable() instanceof ImageDrawable drawable) {
            // 保留 Image、采样和动画状态，只更新 drawable 的固有尺寸。
            drawable.setTargetDensity(densityDpi);
        }
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                refreshChildren(group.getChildAt(index), densityChanged, densityDpi);
            }
        }
        view.forceLayout();
        view.invalidate();
    }
}
