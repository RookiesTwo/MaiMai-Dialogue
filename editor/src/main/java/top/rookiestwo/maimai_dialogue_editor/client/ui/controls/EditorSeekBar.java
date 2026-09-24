package top.rookiestwo.maimai_dialogue_editor.client.ui.controls;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.drawable.LayerDrawable;
import icyllis.modernui.graphics.drawable.ScaleDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.SeekBar;

/** Native SeekBar input with the editor's square styling and explicit gesture cleanup. */
public final class EditorSeekBar extends SeekBar {
    private boolean pointerDown;

    public EditorSeekBar(Context context) {
        super(context);
        setUserAnimationEnabled(false);
        setTickMark(null);
        setBackground(null);
        setThumbTintList(null);
        setProgressTintList(null);
        setProgressBackgroundTintList(null);
        EditorWidgets.bindMetrics(this, () -> {
            setPadding(dp(6), 0, dp(6), 0);
            setMinimumHeight(dp(EditorWidgets.COMPACT_CONTROL_DP));
            var thumb = EditorWidgets.shape(EditorWidgets.ACCENT, 0);
            thumb.setSize(dp(6), dp(12));
            setThumb(thumb);
            var track = new LayerDrawable(EditorWidgets.shape(EditorWidgets.BORDER, 0),
                    new ScaleDrawable(EditorWidgets.shape(EditorWidgets.ACCENT, 0), Gravity.LEFT, 1, -1));
            track.setId(0, R.id.background);
            track.setId(1, R.id.progress);
            for (int index = 0; index < 2; index++) {
                track.setLayerGravity(index, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL);
                track.setLayerHeight(index, dp(2));
            }
            setProgressDrawable(track);
            setSplitTrack(false);
        });
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            if (!isEnabled() || !event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) return false;
            pointerDown = true;
        } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            if (!pointerDown) return false;
            if (action == MotionEvent.ACTION_MOVE && !event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) {
                cancelGesture();
                return true;
            }
        }
        boolean handled = super.onTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) pointerDown = false;
        return handled;
    }

    public void cancelGesture() {
        if (!pointerDown) return;
        pointerDown = false;
        MotionEvent cancel = MotionEvent.obtain(Core.timeNanos(), MotionEvent.ACTION_CANCEL, 0, 0, 0);
        try { super.onTouchEvent(cancel); }
        finally { cancel.recycle(); }
        setPressed(false);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) cancelGesture();
    }

    @Override protected void onDetachedFromWindow() {
        cancelGesture();
        super.onDetachedFromWindow();
    }
}
