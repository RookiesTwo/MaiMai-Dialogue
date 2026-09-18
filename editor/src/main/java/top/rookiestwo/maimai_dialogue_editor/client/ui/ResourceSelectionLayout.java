package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;

/** A single selection background in scroll-content coordinates; row hit targets never move. */
final class ResourceSelectionLayout extends LinearLayout {
    private static final int DURATION_MS = 160;
    private final Paint paint = new Paint();
    private final Rect target = new Rect();
    private ValueAnimator animator;
    private boolean visible;
    private float left, top, right, bottom;
    private float density;

    ResourceSelectionLayout(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        paint.setColor(EditorWidgets.SELECTION);
        paint.setAntiAlias(false);
    }

    void select(Rect bounds, boolean animate, int scrollDelta) {
        float nextDensity = getContext().getResources().getDisplayMetrics().density;
        boolean move = animate && visible && isShown() && density == nextDensity;
        cancelAnimation();
        density = nextDensity;
        target.set(bounds);
        visible = true;
        if (!move) {
            snapToTarget();
            return;
        }
        // Auto-reveal can scroll the list first. Preserve the band's current screen position.
        top += scrollDelta;
        bottom += scrollDelta;
        float fromLeft = left, fromTop = top, fromRight = right, fromBottom = bottom;
        ValueAnimator next = ValueAnimator.ofFloat(0.0F, 1.0F);
        next.setDuration(DURATION_MS);
        next.setInterpolator(TimeInterpolator.LINEAR);
        next.addUpdateListener(value -> {
            if (animator != next) return;
            float fraction = value.getAnimatedFraction();
            float eased = 1.0F - (float) Math.pow(1.0F - fraction, 3);
            left = fromLeft + (target.left - fromLeft) * eased;
            top = fromTop + (target.top - fromTop) * eased;
            right = fromRight + (target.right - fromRight) * eased;
            bottom = fromBottom + (target.bottom - fromBottom) * eased;
            if (fraction >= 1.0F) animator = null;
            invalidate();
        });
        animator = next;
        next.start();
    }

    void syncBounds(Rect bounds) {
        if (!visible || !target.equals(bounds)
                || density != getContext().getResources().getDisplayMetrics().density) {
            select(bounds, false, 0);
        }
    }

    void clearSelection() {
        cancelAnimation();
        visible = false;
        invalidate();
    }

    private void cancelAnimation() {
        ValueAnimator current = animator;
        animator = null;
        if (current != null) current.cancel();
    }

    private void snapToTarget() {
        left = target.left;
        top = target.top;
        right = target.right;
        bottom = target.bottom;
        invalidate();
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (visible) canvas.drawRect(left, top, right, bottom, paint);
    }

    @Override protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) {
            cancelAnimation();
            if (visible) snapToTarget();
        }
    }

    @Override protected void onDetachedFromWindow() {
        clearSelection();
        super.onDetachedFromWindow();
    }
}
