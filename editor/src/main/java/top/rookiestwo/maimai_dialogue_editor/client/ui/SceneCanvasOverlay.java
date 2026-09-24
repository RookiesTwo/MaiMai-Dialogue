package top.rookiestwo.maimai_dialogue_editor.client.ui;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.*;
import icyllis.modernui.view.*;
import top.rookiestwo.maimai_dialogue_editor.document.SceneWorkspace;
import java.util.Objects;

/** Nine-point selection and transform handles. Runtime geometry drives sizing; history belongs to the model. */
final class SceneCanvasOverlay extends View {
    private enum Handle {
        TOP_LEFT(0, 0), TOP(0.5f, 0), TOP_RIGHT(1, 0), LEFT(0, 0.5f), CENTER(0.5f, 0.5f),
        RIGHT(1, 0.5f), BOTTOM_LEFT(0, 1), BOTTOM(0.5f, 1), BOTTOM_RIGHT(1, 1);
        final float x, y;
        Handle(float x, float y) { this.x = x; this.y = y; }
        boolean resizeX() { return x != 0.5f; }
        boolean resizeY() { return y != 0.5f; }
        float x(RectF bounds) { return bounds.left + bounds.width() * x; }
        float y(RectF bounds) { return bounds.top + bounds.height() * y; }
    }
    interface Host {
        boolean canInteract();
        java.util.Map<String, RectF> objectBounds();
        RectF objectAnchor(String id);
        String objectId();
        void selectObject(String id);
        boolean beginPositionDrag(String id);
        SceneWorkspace.Transform dragPosition();
        void endPositionDrag(boolean commit);
        void moveObject(float dx, float dy);
        void resizeObject(SceneWorkspace.Transform origin, float dx, float dy, float scaleX, float scaleY);
        default boolean uniformResize() { return false; }
        default boolean canResize() { return true; }
    }
    private final Host host;
    private final Paint paint = new Paint();
    private String hovered = "";
    private boolean dragging, moved, shiftPressed;
    private float startX, startY;
    private float lastDeltaX, lastDeltaY;
    private Handle activeHandle, hoverHandle;
    private RectF initialBounds, initialAnchor;
    private SceneWorkspace.Transform initialTransform;

    SceneCanvasOverlay(Context context, EditorScenePreviewView host, SceneWorkspace model) {
        this(context, new Host() {
            public boolean canInteract() { return host.canInteract(); }
            public java.util.Map<String, RectF> objectBounds() { return host.objectBounds(); }
            public RectF objectAnchor(String id) { return host.objectAnchor(id); }
            public String objectId() { return model.objectId(); }
            public void selectObject(String id) { model.selectObject(id); }
            public boolean beginPositionDrag(String id) { return model.beginPositionDrag(id); }
            public SceneWorkspace.Transform dragPosition() { return model.dragPosition(); }
            public void endPositionDrag(boolean commit) { model.endPositionDrag(commit); }
            public void moveObject(float dx, float dy) { host.moveObject(dx, dy); }
            public void resizeObject(SceneWorkspace.Transform origin, float dx, float dy, float sx, float sy) {
                host.resizeObject(origin, dx, dy, sx, sy);
            }
        });
    }
    SceneCanvasOverlay(Context context, Host host) {
        super(context); this.host = host;
        setFocusable(true); setFocusableInTouchMode(true); setClickable(true); setWillNotDraw(false);
        paint.setAntiAlias(true);
    }
    private String hit(float x, float y) {
        if (!host.canInteract()) return "";
        String hit = "";
        for (var entry : host.objectBounds().entrySet()) if (entry.getValue().contains(x, y)) hit = entry.getKey();
        return hit;
    }
    private Handle hitHandle(float x, float y) {
        if (!host.canInteract()) return null;
        var bounds = host.objectBounds().get(host.objectId()); if (bounds == null) return null;
        float radius = Math.max(4, dp(6)), nearest = Float.MAX_VALUE;
        Handle found = null;
        for (Handle handle : Handle.values()) {
            if (handle != Handle.CENTER && !host.canResize()) continue;
            float dx = x - handle.x(bounds), dy = y - handle.y(bounds), distance = dx * dx + dy * dy;
            if (Math.abs(dx) <= radius && Math.abs(dy) <= radius && distance < nearest) { found = handle; nearest = distance; }
        }
        return found;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                if (!event.isButtonPressed(MotionEvent.BUTTON_PRIMARY) || !host.canInteract()) return false;
                requestFocus(); startX = event.getX(); startY = event.getY(); moved = false;
                shiftPressed = event.isShiftPressed();
                lastDeltaX = lastDeltaY = 0;
                activeHandle = hitHandle(startX, startY);
                String id = activeHandle == null ? hit(startX, startY) : host.objectId();
                host.selectObject(id); hovered = id;
                if (id.isEmpty()) { invalidate(); return false; }
                initialBounds = host.objectBounds().get(id); initialAnchor = host.objectAnchor(id);
                if (!id.isEmpty() && initialBounds != null && initialAnchor != null) {
                    dragging = host.beginPositionDrag(id); initialTransform = host.dragPosition();
                }
                if (dragging) { setPressed(true); getParent().requestDisallowInterceptTouchEvent(true); }
                invalidate(); return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false;
                if (!event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) { finish(true); return true; }
                move(event); return true;
            }
            case MotionEvent.ACTION_UP -> {
                if (dragging) { shiftPressed = event.isShiftPressed(); move(event); finish(true); }
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> { boolean handled = dragging; finish(false); return handled; }
            default -> { return dragging; }
        }
    }
    private void move(MotionEvent event) {
        float dx = event.getX() - startX, dy = event.getY() - startY;
        if (!moved && Math.hypot(dx, dy) < dp(3)) return;
        moved = true; lastDeltaX = dx; lastDeltaY = dy;
        // ModernUI's Minecraft bridge emits ACTION_MOVE with modifiers = 0.
        // Only button/key events carry a usable Shift state; movement must not clear it.
        applyPointer();
    }
    private void updateShift(boolean pressed) {
        if (shiftPressed == pressed) return;
        shiftPressed = pressed;
        applyPointer();
    }
    private void applyPointer() {
        if (!dragging || !moved || initialTransform == null) return;
        if (activeHandle == null || activeHandle == Handle.CENTER) { host.moveObject(lastDeltaX, lastDeltaY); return; }
        float hx = activeHandle.x(initialBounds), hy = activeHandle.y(initialBounds);
        float px = initialBounds.left + initialBounds.width() * (1 - activeHandle.x);
        float py = initialBounds.top + initialBounds.height() * (1 - activeHandle.y);
        float vx = hx - px, vy = hy - py;
        boolean horizontal = activeHandle.resizeX(), vertical = activeHandle.resizeY();
        float sx = horizontal ? 1 + lastDeltaX / vx : 1;
        float sy = vertical ? 1 + lastDeltaY / vy : 1;
        float minX = Math.max(1, dp(2)) / Math.max(1, initialBounds.width());
        float minY = Math.max(1, dp(2)) / Math.max(1, initialBounds.height());
        float scaleX, scaleY;
        if (shiftPressed || host.uniformResize()) {
            // Remove the existing axis stretch before projecting onto the image's original diagonal.
            // FIT_CENTER and the common scale preserve aspect; equal absolute axis scales restore it.
            double baseX = (double) vx / initialTransform.scaleX();
            double baseY = (double) vy / initialTransform.scaleY();
            double uniform = horizontal && vertical
                    ? ((vx + (double) lastDeltaX) * baseX + (vy + (double) lastDeltaY) * baseY)
                    / (baseX * baseX + baseY * baseY)
                    : horizontal ? (vx + (double) lastDeltaX) / baseX : (vy + (double) lastDeltaY) / baseY;
            scaleX = scaleY = (float) Math.max(Math.max((double) minX * initialTransform.scaleX(),
                    (double) minY * initialTransform.scaleY()), uniform);
            sx = scaleX / initialTransform.scaleX();
            sy = scaleY / initialTransform.scaleY();
        } else {
            sx = horizontal ? Math.max(minX, sx) : 1; sy = vertical ? Math.max(minY, sy) : 1;
            scaleX = initialTransform.scaleX() * sx; scaleY = initialTransform.scaleY() * sy;
        }
        // Scaling the actual image about the opposite handle also moves the object's configured anchor.
        host.resizeObject(initialTransform, (initialAnchor.left - px) * (sx - 1),
                (initialAnchor.top - py) * (sy - 1), scaleX, scaleY);
    }
    void finish(boolean commit) {
        boolean hadGesture = dragging;
        abandonGesture();
        if (hadGesture) host.endPositionDrag(commit);
    }
    void synchronize() {
        if (dragging && host.dragPosition() == null) abandonGesture();
        if (!host.canInteract() && !dragging) { hovered = ""; hoverHandle = null; }
        invalidate();
    }
    private void abandonGesture() {
        dragging = false; moved = false; shiftPressed = false; activeHandle = null; initialTransform = null; initialBounds = null; initialAnchor = null; setPressed(false);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }
    @Override public boolean onHoverEvent(MotionEvent event) {
        Handle handle = event.getAction() == MotionEvent.ACTION_HOVER_EXIT ? null : hitHandle(event.getX(), event.getY());
        if (handle != hoverHandle) { hoverHandle = handle; invalidate(); }
        String next = event.getAction() == MotionEvent.ACTION_HOVER_EXIT ? "" : handle == null ? hit(event.getX(), event.getY()) : host.objectId();
        if (!Objects.equals(next, hovered)) { hovered = next; invalidate(); }
        return !next.isEmpty() || super.onHoverEvent(event);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        var bounds = host.objectBounds(); String selected = host.objectId();
        RectF hover = bounds.get(hovered);
        if (hover != null && !hovered.equals(selected)) {
            paint.setStroke(false); paint.setColor(0x180088FF); canvas.drawRect(hover, paint);
            paint.setStroke(true); paint.setStrokeWidth(Math.max(1, dp(1))); paint.setColor(EditorWidgets.SPLITTER_HOVER);
            canvas.drawRect(hover, paint);
        }
        RectF selectedBounds = bounds.get(selected);
        if (selectedBounds == null) return;
        paint.setStroke(true); paint.setStrokeWidth(Math.max(1, dp(1))); paint.setColor(EditorWidgets.ACCENT);
        canvas.drawRect(selectedBounds, paint);
        RectF anchor = host.objectAnchor(selected);
        if (anchor != null) {
            float x = anchor.left, y = anchor.top, radius = dp(5);
            paint.setStrokeWidth(Math.max(1, dp(2)));
            canvas.drawLine(x - radius, y, x + radius, y, paint);
            canvas.drawLine(x, y - radius, x, y + radius, paint);
        }
        float radius = Math.max(2, dp(3));
        for (Handle handle : Handle.values()) {
            if (handle != Handle.CENTER && !host.canResize()) continue;
            float x = handle.x(selectedBounds), y = handle.y(selectedBounds);
            paint.setStroke(false);
            paint.setColor(handle == activeHandle || handle == hoverHandle ? EditorWidgets.ACCENT : EditorWidgets.PANEL);
            canvas.drawRect(x - radius, y - radius, x + radius, y + radius, paint);
            paint.setStroke(true); paint.setStrokeWidth(Math.max(1, dp(1))); paint.setColor(EditorWidgets.ACCENT);
            canvas.drawRect(x - radius, y - radius, x + radius, y + radius, paint);
        }
    }
    @Override public PointerIcon onResolvePointerIcon(MotionEvent event) {
        return dragging || hitHandle(event.getX(), event.getY()) != null || !hit(event.getX(), event.getY()).isEmpty()
                ? PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND) : super.onResolvePointerIcon(event);
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEY_ESCAPE && dragging) { finish(false); return true; }
        if (dragging && (keyCode == KeyEvent.KEY_LEFT_SHIFT || keyCode == KeyEvent.KEY_RIGHT_SHIFT)) { updateShift(true); return true; }
        return super.onKeyDown(keyCode, event);
    }
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (dragging && (keyCode == KeyEvent.KEY_LEFT_SHIFT || keyCode == KeyEvent.KEY_RIGHT_SHIFT)) {
            updateShift(event.isShiftPressed()); return true;
        }
        return super.onKeyUp(keyCode, event);
    }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width != oldWidth || height != oldHeight) finish(true);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused); if (!focused) finish(true);
    }
    @Override protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous); if (!focused) finish(true);
    }
    @Override protected void onDetachedFromWindow() { finish(false); super.onDetachedFromWindow(); }
}
