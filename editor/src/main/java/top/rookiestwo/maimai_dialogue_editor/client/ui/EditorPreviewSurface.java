package top.rookiestwo.maimai_dialogue_editor.client.ui;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.fragment.FragmentContainerView;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

/** Measure the real UI at game-window scale, then fit its complete View tree into the preview. */
final class EditorPreviewSurface extends FrameLayout {
    private final View content;
    private int referenceHeight = 1;
    private int logicalWidth;
    private int logicalHeight;
    private float scale = 1.0F;

    EditorPreviewSurface(Context context, int containerId) {
        this(context, new FragmentContainerView(context));
        content.setId(containerId);
    }

    EditorPreviewSurface(Context context, View content) {
        super(context);
        setClipChildren(true);
        this.content = content;
        content.setPivotX(0);
        content.setPivotY(0);
        addView(content);
    }

    void setReferenceHeight(int height) {
        int next = Math.max(1, height);
        if (referenceHeight == next) return;
        referenceHeight = next;
        requestLayout();
    }

    void mapContentBounds(RectF bounds) {
        if (!content.hasIdentityMatrix()) content.getMatrix().mapRect(bounds);
        bounds.offset(content.getLeft() + getLeft(), content.getTop() + getTop());
    }

    float normalizedDeltaX(float distance) { return distance / Math.max(1, content.getWidth() * content.getScaleX()); }
    float normalizedDeltaY(float distance) { return distance / Math.max(1, content.getHeight() * content.getScaleY()); }

    // 在缩放边界将坐标换算为内容坐标，事件本身只保留平移，避免子控件偏移被再次缩放。
    private MotionEvent contentEvent(MotionEvent event) {
        float[] point = {event.getX() + getScrollX() - content.getLeft(),
                event.getY() + getScrollY() - content.getTop()};
        if (!content.hasIdentityMatrix()) content.getInverseMatrix().mapPoint(point);
        MotionEvent mapped = event.copy();
        mapped.offsetLocation(point[0] - event.getX(), point[1] - event.getY());
        return mapped;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (content.getVisibility() != VISIBLE) return false;
        MotionEvent mapped = contentEvent(event);
        try { return content.dispatchTouchEvent(mapped); }
        finally { mapped.recycle(); }
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (content.getVisibility() != VISIBLE) return false;
        MotionEvent mapped = contentEvent(event);
        try { return content.dispatchGenericMotionEvent(mapped); }
        finally { mapped.recycle(); }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        logicalHeight = referenceHeight;
        logicalWidth = Math.max(1, (int) Math.round(logicalHeight * 16.0 / 9.0));
        EditorWidgets.measureExact(content, logicalWidth, logicalHeight);
        boolean visible = width > 0 && height > 0;
        content.setVisibility(visible ? VISIBLE : INVISIBLE);
        // Keep an invertible transform even while the panel temporarily has no drawable area.
        scale = visible ? Math.min(width / (float) logicalWidth, height / (float) logicalHeight) : 1.0F;
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        content.layout(0, 0, logicalWidth, logicalHeight);
        content.setScaleX(scale);
        content.setScaleY(scale);
        content.setTranslationX((right - left - logicalWidth * scale) / 2.0F);
        content.setTranslationY((bottom - top - logicalHeight * scale) / 2.0F);
        // 绘制和命中共用此变换；事件在上面的分发入口换算一次。
    }
}
