package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;

/** Resource ID input and its search picker share one property value slot. */
final class EditorReferenceField extends LinearLayout {
    private final Button picker;

    EditorReferenceField(EditText input, Button picker) {
        super(input.getContext());
        this.picker = picker;
        setGravity(Gravity.CENTER_VERTICAL);
        input.setMinWidth(0);
        input.setMinimumWidth(0);
        addView(input, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        picker.setText("▾");
        picker.setGravity(Gravity.CENTER);
        picker.setTooltipText(EditorWidgets.tr("edit.choose_resource"));
        addView(picker);
        EditorWidgets.bindMetrics(picker, () -> {
            picker.setTextSize(14);
            picker.setPadding(0, 0, 0, 0);
            var params = EditorWidgets.squareIconParams(picker);
            params.leftMargin = dp(4);
            picker.setLayoutParams(params);
        });
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int available = Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
        var params = (LayoutParams) picker.getLayoutParams();
        params.width = Math.min(dp(EditorWidgets.COMPACT_CONTROL_DP), available);
        params.height = params.width;
        params.leftMargin = Math.min(dp(4), available - params.width);
        super.onMeasure(widthSpec, heightSpec);
    }
}
