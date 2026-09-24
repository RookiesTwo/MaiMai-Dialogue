package top.rookiestwo.maimai_dialogue_editor.client.ui;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.*;
import icyllis.modernui.view.*;
import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue_editor.preview.ActionTimeline;

/** One ruler/track strip. Pointer capture belongs to this View; the transport belongs to the host. */
final class ActionTimelineStrip extends View {
    static final int EDGE_INSET_DP = 10;
    static final int RULER_HANDLE_DP = 8;
    private final EditorPreviewHost host;
    private ActionTimeline.Lane lane;
    private final Runnable select;
    private final EditorActionKeyframes keyframes;
    private final Paint paint = new Paint();
    private final float[] headVertices = new float[6];
    private ScenePlayback dragging;
    private ScenePlayback contextPlayback;
    private int contextTime;
    private float contextX, contextY, hoverX, hoverY;
    private boolean pointerInside;
    private boolean contextTouchUpPending;
    ActionTimelineStrip(Context context, EditorPreviewHost host, ActionTimeline.Lane lane, Runnable select, EditorActionKeyframes keyframes) {
        super(context); this.host = host; this.lane = lane; this.select = select; this.keyframes = keyframes;
        setClickable(true); setFocusable(true); setFocusableInTouchMode(true); setWillNotDraw(false);
        paint.setAntiAlias(true);
    }
    private int duration() { return host.timelinePlayback() == null ? 0 : host.timeline().duration(); }
    void updateLane(ActionTimeline.Lane next) {
        lane = next;
        if (dragging != null && dragging != host.timelinePlayback()) finish();
        invalidate();
    }
    private float inset() { return Math.min(dp(EDGE_INSET_DP), getWidth() / 2f); }
    private float span() { return Math.max(0, getWidth() - 2 * inset()); }
    private float x(int time) { return inset() + Math.clamp(time / (float)Math.max(1, duration()), 0, 1) * span(); }
    private int time(float x) { return (int)Math.round(Math.clamp((x - inset()) / Math.max(1.0, span()), 0, 1) * duration()); }
    private float headCenter() {
        float width = Math.max(1, dp(1));
        return Math.round(x(host.timelinePlayback() == null ? 0 : host.timeline().position()) - width / 2) + width / 2;
    }
    private boolean hitHead(float x, float y) {
        return host.canSeekTimeline() && Math.abs(x - headCenter()) <= dp(6)
                && y >= (lane == null ? Math.max(0, getHeight() - dp(RULER_HANDLE_DP)) : 0) && y < getHeight();
    }
    void refreshPointerHover() { host.playheadHover(this, pointerInside && hitHead(hoverX, hoverY)); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setColor(EditorWidgets.BORDER);
        for (int i = 0; i <= 4; i++) {
            float x = inset() + i / 4f * span();
            canvas.drawRect(x, lane == null ? Math.max(0, getHeight() - dp(RULER_HANDLE_DP)) : 0, x + 1, getHeight(), paint);
        }
        if (lane != null) {
            float top = dp(4), bottom = Math.max(top, getHeight() - dp(4));
            boolean readOnly = lane.callIndex() < 0;
            boolean selected = !readOnly && host.workspace().actions().selected() == lane.callIndex();
            paint.setColor(readOnly ? EditorWidgets.HEADER : selected ? EditorWidgets.SPLITTER_HOVER : EditorWidgets.SELECTION);
            float start = x(lane.startMs()), end = Math.max(start + 2, x(lane.endMs()));
            canvas.drawRect(start, top, end, bottom, paint);
            paint.setColor(readOnly ? EditorWidgets.BORDER : EditorWidgets.ACCENT);
            paint.setStyle(Paint.STROKE); paint.setStrokeWidth(1);
            canvas.drawRect(start, top, end, bottom, paint);
            paint.setStyle(Paint.FILL);
            for (var marker : lane.markers()) {
                float center = x(marker.timeMs());
                canvas.drawRect(center - dp(2), getHeight() / 2f - dp(3), center + dp(2), getHeight() / 2f + dp(3), paint);
            }
        }
        paint.setColor(EditorWidgets.ACCENT);
        float lineWidth = Math.max(1, dp(1));
        // Snap the line edges to pixels, then use that exact center for both the triangle and stem.
        float head = headCenter();
        if (host.playheadHovered()) {
            paint.setColor(EditorWidgets.SELECTION);
            canvas.drawRect(head - dp(3), lane == null ? Math.max(0, getHeight() - dp(RULER_HANDLE_DP)) : 0, head + dp(3), getHeight(), paint);
            paint.setColor(EditorWidgets.SPLITTER_HOVER);
        }
        float stemTop = 0;
        if (lane == null) {
            float top = Math.max(0, getHeight() - dp(RULER_HANDLE_DP));
            float half = Math.max(0, Math.min(dp(4), inset() - lineWidth / 2));
            stemTop = Math.min(getHeight(), top + dp(6));
            headVertices[0] = head - half; headVertices[1] = top;
            headVertices[2] = head + half; headVertices[3] = top;
            headVertices[4] = head; headVertices[5] = stemTop;
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, headVertices.length, headVertices, 0,
                    null, 0, null, 0, null, 0, 0, null, paint);
        }
        canvas.drawRect(head - lineWidth / 2, stemTop, head + lineWidth / 2, getHeight(), paint);
    }
    private boolean beginContext(MotionEvent event) {
        if (contextPlayback != null) return true;
        if (!host.canSeekTimeline()) return false;
        finish(); requestFocus();
        if (!host.canSeekTimeline()) return false;
        contextPlayback = host.timelinePlayback(); contextX = event.getX(); contextY = event.getY();
        contextTouchUpPending = true;
        contextTime = hitHead(contextX, contextY) ? host.timeline().position() : time(contextX);
        host.seekTimeline(contextPlayback, contextTime);
        return true;
    }
    private boolean endContext() {
        if (contextPlayback == null) return false;
        var expected = contextPlayback; int time = contextTime; float x = contextX, y = contextY;
        contextPlayback = null;
        // ModernUI emits both generic button events and touch events. Open only after release/dispatch.
        post(() -> {
            if (isAttachedToWindow() && expected == host.timelinePlayback()) keyframes.show(this, x, y, lane == null ? null : lane.callIndex(), time);
        });
        return true;
    }
    private void move(float x) {
        if (dragging != host.timelinePlayback() || !host.canSeekTimeline()) { finish(); return; }
        int time = time(x);
        if (lane != null) for (var marker : lane.markers()) if (Math.abs(x(marker.timeMs()) - x) <= dp(4)) { time = marker.timeMs(); break; }
        host.seekTimeline(dragging, time);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) return beginContext(event);
                if (!host.canSeekTimeline() || !event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) return false;
                requestFocus(); select.run(); dragging = host.timelinePlayback();
                setPressed(true); getParent().requestDisallowInterceptTouchEvent(true); move(event.getX()); return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (contextPlayback != null) return true;
                if (dragging == null) return false;
                if (!event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) finish(); else move(event.getX()); return true;
            }
            case MotionEvent.ACTION_UP -> {
                if (contextTouchUpPending) { contextTouchUpPending = false; endContext(); return true; }
                if (dragging == null) return false; move(event.getX()); finish(); return true;
            }
            case MotionEvent.ACTION_CANCEL -> { boolean handled = dragging != null || contextPlayback != null; contextPlayback = null; contextTouchUpPending = false; finish(); return handled; }
            default -> { return dragging != null; }
        }
    }
    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getActionButton() == MotionEvent.BUTTON_SECONDARY) {
            if (event.getAction() == MotionEvent.ACTION_BUTTON_PRESS) return beginContext(event);
            if (event.getAction() == MotionEvent.ACTION_BUTTON_RELEASE) return endContext();
        }
        return super.onGenericMotionEvent(event);
    }
    @Override public boolean onHoverEvent(MotionEvent event) {
        pointerInside = event.getAction() != MotionEvent.ACTION_HOVER_EXIT; hoverX = event.getX(); hoverY = event.getY();
        refreshPointerHover();
        int time = time(event.getX()); String text = time + " ms";
        if (hitHead(event.getX(), event.getY())) text = EditorWidgets.tr("timeline.keyframe.playhead_hint") + " · " + host.timeline().position() + " ms";
        if (lane != null) for (var marker : lane.markers()) if (Math.abs(x(marker.timeMs()) - event.getX()) <= dp(5))
            text += "\n" + EditorWidgets.tr("action." + marker.property()) + " · " + marker.timeMs() + " ms";
        setTooltipText(text); return super.onHoverEvent(event);
    }
    private void clearPointer() { pointerInside = false; contextPlayback = null; contextTouchUpPending = false; host.playheadHover(this, false); }
    private void finish() {
        boolean captured = dragging != null; dragging = null; setPressed(false);
        if (captured && getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (!focused) { finish(); clearPointer(); } }
    @Override protected void onFocusChanged(boolean focused, int direction, Rect previous) { super.onFocusChanged(focused, direction, previous); if (!focused) { finish(); clearPointer(); } }
    @Override protected void onDetachedFromWindow() { finish(); clearPointer(); super.onDetachedFromWindow(); }
}
