package top.rookiestwo.maimai_dialogue.client.config.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;

import java.util.IdentityHashMap;
import java.util.Map;

// 窄窗口改为上下排列，不移除输入框，因此焦点、未提交正文和按键录入继续有效。
final class ConfigOptionRow extends LinearLayout {
    private final Map<View, Dimensions> dimensions = new IdentityHashMap<>();

    ConfigOptionRow(Context context) {
        super(context);
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        LayoutParams params = (LayoutParams) child.getLayoutParams();
        float density = getContext().getResources().getDisplayMetrics().density;
        dimensions.put(child, new Dimensions(
                params.width > 0 ? params.width / density : params.width,
                params.weight, params.leftMargin / density, params.rightMargin / density
        ));
    }

    @Override
    public void onViewRemoved(View child) {
        dimensions.remove(child);
        super.onViewRemoved(child);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int controlsWidth = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            Dimensions original = dimensions.get(child);
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            params.width = original.width > 0 ? dp(original.width) : (int) original.width;
            params.weight = original.weight;
            params.setMargins(dp(original.leftMargin), 0, dp(original.rightMargin), 0);
            if (i > 0) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                controlsWidth += child.getMeasuredWidth() + params.leftMargin + params.rightMargin;
            }
        }
        boolean stacked = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
                && width - getPaddingLeft() - getPaddingRight() - controlsWidth < dp(160);
        setOrientation(stacked ? VERTICAL : HORIZONTAL);
        if (stacked) {
            for (int i = 0; i < getChildCount(); i++) {
                LayoutParams params = (LayoutParams) getChildAt(i).getLayoutParams();
                params.width = LayoutParams.MATCH_PARENT;
                params.weight = 0;
                params.setMargins(0, i == 0 ? 0 : dp(6), 0, 0);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private record Dimensions(float width, float weight, float leftMargin, float rightMargin) {
    }
}
