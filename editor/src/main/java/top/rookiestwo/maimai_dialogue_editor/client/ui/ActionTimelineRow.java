package top.rookiestwo.maimai_dialogue_editor.client.ui;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.FrameLayout;

/** Shared column sizing for the fixed ruler and the vertically scrolling action rows. */
final class ActionTimelineRow extends FrameLayout {
    private final View label, track;
    ActionTimelineRow(Context context, View label, View track) {
        super(context); this.label = label; this.track = track; addView(label); addView(track);
    }
    private int labelWidth(int width) { return Math.max(0, Math.min(dp(200), width / 3)); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec), split = labelWidth(width);
        EditorWidgets.measureExact(label, split, height); EditorWidgets.measureExact(track, width - split, height);
        setMeasuredDimension(width, height);
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int split = labelWidth(r - l); label.layout(0, 0, split, b - t); track.layout(split, 0, r - l, b - t);
    }
}
