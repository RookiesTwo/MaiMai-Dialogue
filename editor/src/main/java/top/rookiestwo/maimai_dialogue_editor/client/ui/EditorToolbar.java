package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.HorizontalScrollView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

// 关闭与恢复按钮固定在两端，业务占位按钮在窄窗口中可以横向滚动。
final class EditorToolbar extends FrameLayout {
    private final Button close;
    private final Button reset;
    private final HorizontalScrollView scroll;
    private int closeWidth;
    private int resetWidth;

    EditorToolbar(Context context, Runnable closeAction, Runnable resetAction) {
        super(context);
        setBackground(EditorWidgets.shape(EditorWidgets.HEADER, 0));
        close = EditorWidgets.icon(context, "×", "close", closeAction);
        reset = EditorWidgets.button(context, "reset_layout", resetAction);
        reset.setTooltipText(EditorWidgets.tr("reset_layout"));
        LinearLayout items = new LinearLayout(context);
        items.setOrientation(LinearLayout.HORIZONTAL);
        items.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = EditorWidgets.label(context, "title", 16, EditorWidgets.ACCENT);
        EditorWidgets.bindMetrics(title, () -> title.setPadding(title.dp(12), 0, title.dp(16), 0));
        items.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        for (String key : new String[]{"project", "save", "undo", "redo", "preview", "export"}) {
            Button button = EditorWidgets.button(context, key, null);
            items.addView(button);
            EditorWidgets.bindMetrics(button, () -> {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
                params.setMargins(0, button.dp(6), button.dp(4), button.dp(6));
                button.setLayoutParams(params);
            });
        }
        scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(items, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(scroll);
        addView(close);
        addView(reset);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        closeWidth = Math.min(dp(36), width);
        resetWidth = Math.min(dp(96), width - closeWidth);
        reset.setText(resetWidth < dp(64) ? "↺" : EditorWidgets.tr("reset_layout"));
        EditorPanel.measureExact(close, closeWidth, height);
        EditorPanel.measureExact(reset, resetWidth, height);
        EditorPanel.measureExact(scroll, width - closeWidth - resetWidth, height);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        close.layout(0, 0, closeWidth, height);
        reset.layout(width - resetWidth, 0, width, height);
        scroll.layout(closeWidth, 0, width - resetWidth, height);
    }
}
