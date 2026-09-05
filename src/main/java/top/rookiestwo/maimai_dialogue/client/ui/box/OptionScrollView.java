package top.rookiestwo.maimai_dialogue.client.ui.box;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.ScrollView;

/**
 * A vertically scrolling Option viewport sized to complete Option rows.
 */
final class OptionScrollView extends ScrollView {
    private int collapsedLimit = 1;
    private int expandedLimit = 1;
    private boolean expanded;

    OptionScrollView(Context context) {
        super(context);
        setFillViewport(false);
        setVerticalScrollBarEnabled(true);
    }

    void setOptionLimits(int collapsedLimit, int expandedLimit) {
        this.collapsedLimit = Math.max(1, collapsedLimit);
        this.expandedLimit = Math.max(
                this.collapsedLimit,
                expandedLimit
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

    boolean isExpandable() {
        View child = getChildCount() == 0 ? null : getChildAt(0);
        return expandedLimit > collapsedLimit
                && optionCount(child) > collapsedLimit;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentMode = MeasureSpec.getMode(heightMeasureSpec);
        int parentLimit = MeasureSpec.getSize(heightMeasureSpec);
        int availableHeight = parentMode == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE
                : parentLimit;

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        View child = getChildCount() == 0 ? null : getChildAt(0);
        int collapsedHeight = viewportHeightFor(
                child,
                collapsedLimit,
                availableHeight
        );
        int expandedHeight = viewportHeightFor(
                child,
                expandedLimit,
                availableHeight
        );
        int viewportHeight = expanded
                ? expandedHeight
                : collapsedHeight;
        super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(
                        viewportHeight,
                        MeasureSpec.EXACTLY
                )
        );

    }

    private int viewportHeightFor(
            View child,
            int optionLimit,
            int availableHeight
    ) {
        if (child == null) {
            return 0;
        }

        int viewportPadding = getPaddingTop() + getPaddingBottom();
        int availableContent = availableHeight == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : Math.max(0, availableHeight - viewportPadding);
        int desiredContent = contentHeightFor(
                child,
                optionLimit,
                availableContent
        );
        if (availableContent != Integer.MAX_VALUE) {
            desiredContent = Math.min(
                    desiredContent,
                    availableContent
            );
        }
        return viewportPadding + desiredContent;
    }

    private static int contentHeightFor(
            View child,
            int optionLimit,
            int availableContent
    ) {
        if (!(child instanceof ViewGroup optionList)) {
            return child.getMeasuredHeight();
        }

        int optionCount = optionList.getChildCount();
        if (optionCount <= optionLimit
                && (availableContent == Integer.MAX_VALUE
                || child.getMeasuredHeight() <= availableContent)) {
            return child.getMeasuredHeight();
        }

        int height = optionList.getPaddingTop()
                + optionList.getPaddingBottom();
        int completeOptions = 0;
        for (int index = 0;
             index < optionCount && completeOptions < optionLimit;
             index++) {
            View option = optionList.getChildAt(index);
            int optionHeight = option.getMeasuredHeight();
            ViewGroup.LayoutParams params = option.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams margins) {
                optionHeight += margins.topMargin + margins.bottomMargin;
            }
            if ((long) height + optionHeight > availableContent) {
                if (completeOptions == 0) {
                    return availableContent;
                }
                break;
            }
            height += optionHeight;
            completeOptions++;
        }
        return height;
    }

    private static int optionCount(View child) {
        return child instanceof ViewGroup optionList
                ? optionList.getChildCount()
                : 0;
    }
}
