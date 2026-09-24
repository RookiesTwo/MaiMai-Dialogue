package top.rookiestwo.maimai_dialogue_editor.client.ui.workbench;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorButtonIcon;
import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;
import org.jetbrains.annotations.Nullable;

// 所有分区共享标题与边框；窄小尺寸时优先保留标题，内容裁剪在本分区内。
final class EditorPanel extends FrameLayout {
    private final TextView title;
    private final View content;
    @Nullable
    private final Button collapse;
    private int inset;
    private int headerHeight;
    private int collapseWidth;

    EditorPanel(Context context, String titleKey, View content, @Nullable Runnable collapseAction, boolean left) {
        super(context);
        this.content = content;
        title = EditorWidgets.label(context, titleKey, 13, EditorWidgets.TEXT);
        title.setBackground(EditorWidgets.shape(EditorWidgets.HEADER, 0));
        EditorWidgets.bindMetrics(title, () -> title.setPadding(title.dp(10), 0,
                title.dp(8 + (collapseAction == null ? 0 : EditorWidgets.COMPACT_CONTROL_DP)), 0));
        addView(title);
        addView(content);
        collapse = collapseAction == null ? null : EditorWidgets.panelIcon(context,
                left ? EditorButtonIcon.CHEVRON_LEFT : EditorButtonIcon.CHEVRON_RIGHT,
                left ? "collapse_left" : "collapse_right", collapseAction);
        if (collapse != null) {
            addView(collapse);
        }
        EditorWidgets.bindMetrics(this, () -> setBackground(
                EditorWidgets.shape(EditorWidgets.PANEL, dp(1))));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        inset = Math.min(dp(1), Math.min(width, height) / 2);
        int innerWidth = Math.max(0, width - inset * 2);
        int innerHeight = Math.max(0, height - inset * 2);
        headerHeight = Math.min(dp(EditorLayout.HEADER_DP), innerHeight);
        collapseWidth = collapse == null ? 0 : Math.min(dp(EditorWidgets.COMPACT_CONTROL_DP), Math.min(innerWidth, headerHeight));
        EditorWidgets.measureExact(title, innerWidth, headerHeight);
        if (collapse != null) {
            EditorWidgets.measureExact(collapse, collapseWidth, collapseWidth);
        }
        EditorWidgets.measureExact(content, innerWidth, innerHeight - headerHeight);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        title.layout(inset, inset, width - inset, inset + headerHeight);
        if (collapse != null) {
            int buttonTop = inset + (headerHeight - collapseWidth) / 2;
            collapse.layout(width - inset - collapseWidth, buttonTop, width - inset, buttonTop + collapseWidth);
        }
        content.layout(inset, inset + headerHeight, width - inset, bottom - top - inset);
    }

}
