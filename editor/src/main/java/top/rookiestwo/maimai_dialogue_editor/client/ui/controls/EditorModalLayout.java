package top.rookiestwo.maimai_dialogue_editor.client.ui.controls;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

/** Shared modal surface; each form supplies its own content, scrolling and focus policy. */
public abstract class EditorModalLayout extends FrameLayout {
    private final int maxWidthDp, maxHeightDp;
    private View panel;

    protected EditorModalLayout(Context context, int maxWidthDp, int maxHeightDp) {
        super(context);
        this.maxWidthDp = maxWidthDp; this.maxHeightDp = maxHeightDp;
        setBackground(EditorWidgets.shape(0x80788088, 0));
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    protected final void setPanel(View panel) {
        this.panel = panel;
        EditorWidgets.bindMetrics(panel, () -> panel.setBackground(EditorWidgets.shape(EditorWidgets.PANEL, dp(1))));
        addView(panel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        if (panel != null) {
            var params = (LayoutParams) panel.getLayoutParams();
            params.width = Math.max(0, Math.min(dp(maxWidthDp), width - Math.min(dp(24), width / 8)));
            params.height = Math.max(0, Math.min(dp(maxHeightDp), height - Math.min(dp(24), height / 8)));
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
