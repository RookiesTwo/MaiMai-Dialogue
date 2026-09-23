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

import java.util.ArrayList;
import java.util.List;

/** A single selection background in scroll-content coordinates; row hit targets never move. */
final class ResourceSelectionLayout extends LinearLayout {
    static final int TOGGLE_WIDTH_DP = 18;
    private static final int INDENT_DP = 12;
    private static final int MAX_INDENT_DEPTH = 6;
    private static final int DURATION_MS = 160;
    private final Paint paint = new Paint();
    private final Paint guidePaint = new Paint();
    private final List<Branch> branches = new ArrayList<>();
    private final List<Branch> ancestors = new ArrayList<>();
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
        guidePaint.setColor(EditorWidgets.BORDER);
        guidePaint.setAntiAlias(false);
    }

    static int indent(int depth) { return Math.min(depth, MAX_INDENT_DEPTH) * INDENT_DP; }

    void clearRows() {
        branches.clear();
        ancestors.clear();
        removeAllViews();
    }

    void addTreeRow(View row, int depth, boolean expanded, View anchor, boolean toggle) {
        addView(row);
        GuideNode node = new GuideNode(row, anchor, toggle);
        while (!ancestors.isEmpty() && ancestors.getLast().depth >= depth) ancestors.removeLast();
        if (!ancestors.isEmpty()) ancestors.getLast().children.add(node);
        if (expanded) {
            Branch branch = new Branch(node, depth);
            branches.add(branch);
            ancestors.add(branch);
        }
    }

    private record GuideNode(View row, View anchor, boolean toggle) {
        int left() { return row.getLeft() + (anchor == row ? 0 : anchor.getLeft()); }
        int centerY() { return row.getTop() + (anchor == row ? 0 : anchor.getTop()) + anchor.getHeight() / 2; }
    }

    private static final class Branch {
        final GuideNode parent;
        final int depth;
        final List<GuideNode> children = new ArrayList<>();
        Branch(GuideNode parent, int depth) { this.parent = parent; this.depth = depth; }
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

    @Override protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        // Draw in the empty space before glyphs, above row backgrounds so hover cannot hide the connectors.
        for (Branch branch : branches) {
            if (branch.children.isEmpty() || branch.depth >= MAX_INDENT_DEPTH) continue;
            GuideNode parent = branch.parent;
            int x = parent.left() + parent.anchor().getWidth() / 2;
            int start = parent.centerY() + dp(5);
            int end = branch.children.getLast().centerY();
            // Stopping at the final child's connector produces the L-shaped branch ending.
            canvas.drawRect(x, start, x + 1, end + 1, guidePaint);
            for (GuideNode child : branch.children) {
                int y = child.centerY();
                int endX = child.left() + (child.toggle()
                        ? child.anchor().getWidth() / 2 - dp(5)
                        : child.anchor().getPaddingLeft() - dp(2));
                if (endX > x) canvas.drawRect(x, y, endX, y + 1, guidePaint);
            }
        }
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
