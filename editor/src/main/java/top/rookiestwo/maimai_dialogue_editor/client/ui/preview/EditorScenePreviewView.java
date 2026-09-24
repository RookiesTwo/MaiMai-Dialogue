package top.rookiestwo.maimai_dialogue_editor.client.ui.preview;

import top.rookiestwo.maimai_dialogue_editor.client.preview.EditorScenePreview;

import top.rookiestwo.maimai_dialogue_editor.client.ui.controls.EditorWidgets;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.widget.*;
import top.rookiestwo.maimai_dialogue.client.scene.*;
import top.rookiestwo.maimai_dialogue.client.ui.scene.*;
import top.rookiestwo.maimai_dialogue_editor.workspace.ProjectWorkspace;
import top.rookiestwo.maimai_dialogue_editor.document.SceneWorkspace;
import java.util.*;

/** Static authoring viewport rendered by the gameplay Scene View. No playback controls or side effects. */
final class EditorScenePreviewView extends FrameLayout {
    private final TextView error;
    private final ProjectWorkspace workspace;
    private final SceneCanvasOverlay overlay;
    private final EditorPreviewSurface surface;
    private final EditorScenePreview preview;
    private boolean framePending;
    private final Runnable frameCallback = this::drawFrame;
    private void drawFrame() {
        framePending = false;
        if (isAttachedToWindow()) { updatePosition(); overlay.invalidate(); }
    }
    private boolean detaching;

    EditorScenePreviewView(Context context, ProjectWorkspace workspace, EditorScenePreview preview, EditorPreviewSurface surface) {
        super(context); this.preview = preview; this.surface = surface; this.workspace = workspace;
        overlay = new SceneCanvasOverlay(context, this, this.workspace.scenes());
        addView(overlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        error = EditorWidgets.paragraph(context, ""); error.setTextColor(EditorWidgets.ERROR);
        error.setMaxLines(4); error.setVisibility(GONE);
        EditorWidgets.bindMetrics(error, () -> error.setPadding(dp(8), dp(6), dp(8), dp(6)));
        addView(error, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        preview.setListener(this::refreshState);
    }
    void refresh() { preview.refresh(); overlay.synchronize(); }
    private void refreshState() {
        String message = preview.error();
        error.setText(message); error.setTooltipText(message); error.setVisibility(message.isEmpty() ? GONE : VISIBLE);
        overlay.invalidate();
    }
    void setReferenceHeight(int height) { surface.setReferenceHeight(height); }
    boolean canInteract() { return preview.canInteract(); }
    Map<String, RectF> objectBounds() {
        if (preview.fragment() == null || surface == null) return Map.of();
        var result = new LinkedHashMap<String, RectF>();
        preview.fragment().visualObjectBounds().forEach((id, bounds) -> { surface.mapContentBounds(bounds); result.put(id, bounds); });
        return result;
    }
    RectF objectAnchor(String id) {
        if (preview.fragment() == null || surface == null) return null;
        var point = preview.fragment().visualObjectAnchor(id).orElse(null); if (point == null) return null;
        var bounds = new RectF(point.x, point.y, point.x, point.y); surface.mapContentBounds(bounds); return bounds;
    }
    void moveObject(float dx, float dy) {
        if (surface != null) workspace.scenes().movePositionDrag(surface.normalizedDeltaX(dx), surface.normalizedDeltaY(dy));
    }
    void resizeObject(SceneWorkspace.Transform origin, float anchorDeltaX, float anchorDeltaY, float scaleX, float scaleY) {
        if (surface != null) workspace.scenes().resizeDrag(origin.x() + surface.normalizedDeltaX(anchorDeltaX),
                origin.y() + surface.normalizedDeltaY(anchorDeltaY), scaleX, scaleY);
    }
    void endDrag(boolean commit) { overlay.finish(commit); }
    void requestFrame(boolean immediate) {
        if (immediate) { removeCallbacks(frameCallback); drawFrame(); return; }
        if (framePending || !isAttachedToWindow()) return;
        framePending = true;
        postOnAnimation(frameCallback);
    }
    private void updatePosition() { preview.updatePosition(); }
    void release() {
        removeCallbacks(frameCallback); framePending = false;
        overlay.finish(false);
        preview.release(detaching);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); preview.setListener(this::refreshState); }
    @Override protected void onDetachedFromWindow() {
        detaching = true;
        try { release(); } finally { preview.setListener(() -> {}); detaching = false; super.onDetachedFromWindow(); }
    }
}
