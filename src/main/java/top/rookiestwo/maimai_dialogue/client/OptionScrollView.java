package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.ScrollView;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A vertically scrolling Option viewport with a collapsed and expanded limit.
 */
final class OptionScrollView extends ScrollView {
    private int collapsedMaxHeight;
    private int expandedMaxHeight;
    private boolean expanded;
    private boolean overflowing;
    private Consumer<Boolean> overflowListener = ignored -> {
    };

    OptionScrollView(Context context) {
        super(context);
        setFillViewport(false);
        setVerticalScrollBarEnabled(true);
    }

    void setHeightLimits(int collapsedMaxHeight, int expandedMaxHeight) {
        this.collapsedMaxHeight = Math.max(1, collapsedMaxHeight);
        this.expandedMaxHeight = Math.max(
                this.collapsedMaxHeight,
                expandedMaxHeight
        );
        requestLayout();
    }

    void setExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            requestLayout();
        }
    }

    boolean isExpanded() {
        return expanded;
    }

    boolean isOverflowing() {
        return overflowing;
    }

    void setOverflowListener(Consumer<Boolean> listener) {
        overflowListener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int configuredLimit = expanded
                ? expandedMaxHeight
                : collapsedMaxHeight;
        int parentMode = MeasureSpec.getMode(heightMeasureSpec);
        int parentLimit = MeasureSpec.getSize(heightMeasureSpec);
        int limit = parentMode == MeasureSpec.UNSPECIFIED
                ? configuredLimit
                : Math.min(configuredLimit, parentLimit);

        super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST)
        );

        View child = getChildCount() == 0 ? null : getChildAt(0);
        boolean nextOverflowing = child != null
                && child.getMeasuredHeight()
                > getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        if (overflowing != nextOverflowing) {
            overflowing = nextOverflowing;
            post(() -> overflowListener.accept(overflowing));
        }
    }
}
