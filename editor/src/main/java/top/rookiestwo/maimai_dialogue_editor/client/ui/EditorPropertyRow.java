package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

/** Inline inspector fields, with stacked labels when the sidebar becomes narrow. */
final class EditorPropertyRow extends LinearLayout {
    private final TextView label;
    private final View control;
    private final boolean multiline;

    EditorPropertyRow(String key, View control, boolean multiline) {
        super(control.getContext());
        this.control = control;
        this.multiline = multiline;
        setGravity(Gravity.CENTER_VERTICAL);
        label = key == null ? null : EditorWidgets.label(getContext(), key, 13, EditorWidgets.MUTED);
        if (label != null) {
            label.setTooltipText(EditorWidgets.tr(key));
            addView(label, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        addView(control, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        EditorWidgets.bindMetrics(this, () -> setPadding(0, dp(1), 0, dp(1)));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int available = Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
        boolean inline = label != null && !multiline && available >= dp(240);
        setOrientation(inline ? HORIZONTAL : VERTICAL);
        int labelWidth = inline ? Math.min(dp(112), Math.round(available * .38f)) : 0;
        if (label != null) {
            LayoutParams params = (LayoutParams) label.getLayoutParams();
            params.width = inline ? labelWidth : LayoutParams.MATCH_PARENT;
            params.height = dp(inline ? EditorWidgets.COMPACT_CONTROL_DP : 20);
            label.setPadding(0, 0, inline ? dp(EditorWidgets.COMPACT_HORIZONTAL_PADDING_DP) : 0, 0);
        }
        LayoutParams params = (LayoutParams) control.getLayoutParams();
        params.width = control instanceof Button ? Math.min(dp(280), available - labelWidth)
                : inline ? 0 : LayoutParams.MATCH_PARENT;
        params.height = control instanceof Button ? dp(EditorWidgets.COMPACT_ROW_DP) : LayoutParams.WRAP_CONTENT;
        params.weight = inline && !(control instanceof Button) ? 1 : 0;
        super.onMeasure(widthSpec, heightSpec);
    }
}
