package top.rookiestwo.maimai_dialogue_editor.client.ui.controls;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.widget.LinearLayout;

/** Content-sized inspector actions, stacked when the sidebar cannot fit the whole row. */
public final class EditorActionRow extends LinearLayout {
    public EditorActionRow(Context context) {
        super(context);
        setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        EditorWidgets.bindMetrics(this, () -> setPadding(dp(3), dp(3), dp(3), dp(3)));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int available = Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
        int total = 0, visible = 0;
        for (int i = 0; i < getChildCount(); i++) {
            var child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(dp(EditorWidgets.COMPACT_CONTROL_DP), MeasureSpec.EXACTLY));
            total += child.getMeasuredWidth() + (visible++ == 0 ? 0 : dp(6));
        }
        boolean stacked = total > available;
        setOrientation(stacked ? VERTICAL : HORIZONTAL);
        int index = 0;
        for (int i = 0; i < getChildCount(); i++) {
            var child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            var params = (LayoutParams) child.getLayoutParams();
            params.width = LayoutParams.WRAP_CONTENT;
            params.height = dp(EditorWidgets.COMPACT_CONTROL_DP);
            params.weight = 0;
            boolean last = ++index == visible;
            params.rightMargin = !stacked && !last ? dp(6) : 0;
            params.bottomMargin = stacked && !last ? dp(4) : 0;
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
