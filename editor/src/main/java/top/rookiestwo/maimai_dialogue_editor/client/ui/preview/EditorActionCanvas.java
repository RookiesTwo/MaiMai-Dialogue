package top.rookiestwo.maimai_dialogue_editor.client.ui.preview;

import top.rookiestwo.maimai_dialogue_editor.client.preview.EditorTimelinePreview;

import com.google.gson.JsonObject;
import icyllis.modernui.core.*;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.widget.FrameLayout;
import top.rookiestwo.maimai_dialogue_editor.client.EditorContentPreparation;
import top.rookiestwo.maimai_dialogue.client.scene.*;
import top.rookiestwo.maimai_dialogue_editor.document.*;
import top.rookiestwo.maimai_dialogue_editor.preview.ActionSceneContext;
import top.rookiestwo.maimai_dialogue_editor.project.*;
import java.util.*;

/** Selected action's canvas adapter; shares handles with initial Scene placement without editing that Scene. */
final class EditorActionCanvas extends FrameLayout implements SceneCanvasOverlay.Host {
    private record Binding(long project, ProjectDraft draft, ActionWorkspace.Context context, int index,
                           ScenePlayback playback, ResolvedActionCall call) {}
    private final EditorTimelinePreview timeline;
    private final EditorPreviewSurface surface;
    private final ProjectWorkspace project;
    private final SceneCanvasOverlay overlay;
    private final Runnable timelineChanged = this::synchronize;
    private Binding binding;
    private JsonObject definition;
    private ActionCanvasEdit candidate;
    private int candidateTime = -1;
    private ActionWorkspace.CanvasGesture gesture;

    EditorActionCanvas(Context context, ProjectWorkspace project, EditorTimelinePreview timeline, EditorPreviewSurface surface) {
        super(context); this.timeline = timeline; this.surface = surface; this.project = project;
        overlay = new SceneCanvasOverlay(context, this);
        addView(overlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }
    private Binding current() {
        var model = project.actions(); var context = model.context();
        if (!model.active() || !model.inspecting() || context == null || !timeline.canSeek()) return null;
        int index = context.standalone() ? 0 : model.selected();
        var call = timeline.model().call(index);
        return call == null ? null : new Binding(project.projectGeneration(), project.draft(), context, index, timeline.playback(), call);
    }
    void synchronize() {
        var next = current();
        if (!Objects.equals(binding, next)) {
            overlay.finish(false); binding = next; definition = null; candidate = null; candidateTime = -1;
            if (next != null) {
                var source = project.actions().definition();
                if (source != null) definition = source.deepCopy();
                else loadReference(next, project.actions().text(true, "action.id", ""));
            }
        }
        if (gesture != null && !gesture.valid()) { gesture.finish(false); gesture = null; }
        overlay.synchronize();
    }
    private void loadReference(Binding expected, String id) {
        EditorContentPreparation.prepare(project, external -> ActionSceneContext.reference(expected.draft(), id, external), (value, failure) -> {
            if (isAttachedToWindow() && binding == expected && Objects.equals(current(), expected)) {
                // The existing action inspector reports unresolved references.
                definition = failure == null ? value : null; candidateTime = -1; overlay.synchronize();
            }
        });
    }
    private ActionCanvasEdit candidate() {
        if (binding == null || definition == null || !timeline.canvasReady() || !Objects.equals(binding, current())) return null;
        int time = timeline.model().position();
        if (candidateTime != time) {
            candidateTime = time; candidate = null;
            try { candidate = new ActionCanvasEdit(binding.playback(), binding.playback().calls().indexOf(binding.call()), time, definition); }
            catch (RuntimeException invalid) { /* Outside the action, hidden/missing target, or invalid draft. */ }
        }
        return candidate;
    }
    public boolean canInteract() {
        if (!hasWindowFocus() || project.actions().editing() && gesture == null) return false;
        var edit = candidate(); return edit != null && edit.canMove();
    }
    public boolean canResize() { var edit = candidate(); return edit != null && edit.canResize(); }
    public boolean uniformResize() { return true; }
    public String objectId() { return binding == null ? "" : binding.call().target(); }
    public void selectObject(String id) { /* Target selection belongs to the action inspector. */ }
    public Map<String, RectF> objectBounds() {
        var fragment = timeline.fragment();
        if (!canInteract() || fragment == null) return Map.of();
        var bounds = fragment.visualObjectBounds().get(objectId());
        if (bounds == null) return Map.of();
        surface.mapContentBounds(bounds); return Map.of(objectId(), bounds);
    }
    public RectF objectAnchor(String id) {
        var fragment = timeline.fragment(); if (fragment == null) return null;
        var point = fragment.visualObjectAnchor(id).orElse(null); if (point == null) return null;
        var bounds = new RectF(point.x, point.y, point.x, point.y); surface.mapContentBounds(bounds); return bounds;
    }
    public boolean beginPositionDrag(String id) {
        if (!canInteract() || !id.equals(objectId())) return false;
        var edit = candidate();
        timeline.seek(binding.playback(), timeline.model().position());
        var expected = binding; int time = timeline.model().position();
        gesture = project.actions().beginCanvas(edit,
                () -> Objects.equals(expected, current()) && timeline.model().manual() && time == timeline.model().position(), this::finishing);
        return gesture != null;
    }
    public SceneWorkspace.Transform dragPosition() {
        if (gesture == null || !gesture.valid()) return null;
        var value = gesture.edit().current();
        return new SceneWorkspace.Transform(objectId(), value.x(), value.y(), value.scale(), value.scale());
    }
    public void moveObject(float dx, float dy) {
        var start = dragPosition(); if (start != null)
            update(start.x() + surface.normalizedDeltaX(dx), start.y() + surface.normalizedDeltaY(dy), start.scaleX());
    }
    public void resizeObject(SceneWorkspace.Transform start, float dx, float dy, float scaleX, float scaleY) {
        if (canResize()) update(start.x() + surface.normalizedDeltaX(dx), start.y() + surface.normalizedDeltaY(dy), scaleX);
    }
    private void update(float x, float y, float scale) {
        if (gesture != null && gesture.update(x, y, scale)) timeline.renderCanvasFrame(binding.playback(), gesture.edit().preview(), false);
    }
    private void finishing(boolean commit) {
        if (gesture == null) return;
        if (commit) timeline.renderCanvasFrame(binding.playback(), gesture.edit().preview(), true);
        else if (binding != null) timeline.renderCanvasFrame(binding.playback(), null, true);
        gesture = null; candidate = null; candidateTime = -1;
        overlay.synchronize();
    }
    public void endPositionDrag(boolean commit) { if (gesture != null) gesture.finish(commit); }
    void finish(boolean commit) { overlay.finish(commit); }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); timeline.addObserver(timelineChanged); synchronize();
    }
    @Override protected void onDetachedFromWindow() {
        timeline.removeObserver(timelineChanged); finish(false); binding = null; definition = null;
        super.onDetachedFromWindow();
    }
}
