package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ScrollView;

/** 菜单与工作台保持在同一 View 树；透明外层接收菜单外点击，面板仍锚定顶栏按钮。 */
final class EditorDropdownMenu extends FrameLayout {
    private final ScrollView scroll;
    private final View anchor;
    private final Runnable onDismiss;
    private final Rect panelBounds = new Rect();
    private final int[] anchorLocation = new int[2];
    private final int[] ownLocation = new int[2];
    private boolean disposed;
    private boolean dismissQueued;

    EditorDropdownMenu(View content, View anchor, Runnable onDismiss) {
        super(content.getContext());
        this.anchor = anchor;
        this.onDismiss = onDismiss;
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        scroll = EditorWidgets.formScroll(content.getContext(), content);
        EditorWidgets.bindMetrics(scroll, () -> scroll.setBackground(
                EditorWidgets.shape(EditorWidgets.PANEL, scroll.dp(1))));
        addView(scroll);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        measurePanel(width, height);
        setMeasuredDimension(width, height);
    }

    private void measurePanel(int width, int height) {
        anchor.getLocationInWindow(anchorLocation);
        getLocationInWindow(ownLocation);
        int panelWidth = Math.min(dp(380), width);
        int left = Math.clamp(anchorLocation[0] - ownLocation[0], 0, width - panelWidth);
        int top = Math.clamp(anchorLocation[1] - ownLocation[1] + anchor.getHeight(), 0, height);
        scroll.measure(MeasureSpec.makeMeasureSpec(panelWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height - top, MeasureSpec.AT_MOST));
        panelBounds.set(left, top, left + panelWidth, top + scroll.getMeasuredHeight());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // 父布局先放置工具栏，再按本次缩放后的锚点位置放置菜单。
        measurePanel(right - left, bottom - top);
        scroll.layout(panelBounds.left, panelBounds.top, panelBounds.right, panelBounds.bottom);
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        if (disposed || dismissQueued) return true;
        if (event.getAction() == MotionEvent.ACTION_DOWN && !insidePanel(event)) {
            dismissQueued = true;
            // 消耗整个外部点击；在事件分发结束后移除自己，避免改变当前触摸目标。
            post(() -> {
                if (!disposed && isAttachedToWindow()) onDismiss.run();
            });
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(@NonNull MotionEvent event) {
        return !insidePanel(event) || super.dispatchGenericMotionEvent(event);
    }

    private boolean insidePanel(MotionEvent event) {
        return panelBounds.contains((int) event.getX(), (int) event.getY());
    }

    // 只释放当前菜单 View；旧菜单的延迟回调不能取消后来打开的表单。
    void dispose() {
        disposed = true;
    }
}
