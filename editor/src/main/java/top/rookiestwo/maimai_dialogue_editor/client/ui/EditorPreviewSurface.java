package top.rookiestwo.maimai_dialogue_editor.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.FragmentContainerView;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.widget.FrameLayout;

/** Measure the real UI at game-window scale, then fit its complete View tree into the preview. */
final class EditorPreviewSurface extends FrameLayout {
    private final FragmentContainerView content;
    private int referenceHeight = 1;
    private int logicalWidth;
    private int logicalHeight;
    private float scale = 1.0F;

    EditorPreviewSurface(Context context, int containerId) {
        super(context);
        setClipChildren(true);
        content = new FragmentContainerView(context);
        content.setId(containerId);
        content.setPivotX(0);
        content.setPivotY(0);
        addView(content);
    }

    void setReferenceHeight(int height) {
        int next = Math.max(1, height);
        if (referenceHeight == next) return;
        referenceHeight = next;
        requestLayout();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        logicalHeight = referenceHeight;
        logicalWidth = Math.max(1, (int) Math.round(logicalHeight * 16.0 / 9.0));
        EditorPanel.measureExact(content, logicalWidth, logicalHeight);
        boolean visible = width > 0 && height > 0;
        content.setVisibility(visible ? VISIBLE : INVISIBLE);
        // Keep an invertible transform even while the panel temporarily has no drawable area.
        scale = visible ? Math.min(width / (float) logicalWidth, height / (float) logicalHeight) : 1.0F;
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        content.layout(0, 0, logicalWidth, logicalHeight);
        content.setScaleX(scale);
        content.setScaleY(scale);
        content.setTranslationX((right - left - logicalWidth * scale) / 2.0F);
        content.setTranslationY((bottom - top - logicalHeight * scale) / 2.0F);
        // ModernUI maps pointer, hover and wheel coordinates through the child View's inverse transform.
    }
}
