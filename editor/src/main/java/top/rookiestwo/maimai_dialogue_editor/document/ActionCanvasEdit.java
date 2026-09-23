package top.rookiestwo.maimai_dialogue_editor.document;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import top.rookiestwo.maimai_dialogue.client.scene.*;
import top.rookiestwo.maimai_dialogue.presentation.action.SceneAction;
import java.math.BigDecimal;
import java.util.*;

/** A silent, immutable sampling transaction. Canvas coordinates are relative to the Step's starting state. */
public final class ActionCanvasEdit {
    private final ScenePlayback playback;
    private final int callIndex, time;
    private final ResolvedActionCall call;
    private final JsonObject original;
    private final SceneObjectState start, current;
    private final Set<String> writable = new HashSet<>(List.of("x", "y", "scale"));
    private JsonObject definition;
    private ScenePlayback preview;

    public ActionCanvasEdit(ScenePlayback playback, int callIndex, int time, JsonObject definition) {
        this.playback = playback; this.callIndex = callIndex; this.time = time;
        call = playback.calls().get(callIndex);
        if (call.action().audioOnly() || call.action().durationMs() <= 0 || time < call.delayMs() || time > call.endTimeMs())
            throw new IllegalArgumentException("The playhead must be inside a visual action with positive duration.");
        original = definition.deepCopy();
        if (!ActionFields.errors(original).isEmpty() || !decode(original).equals(call.action()))
            throw new IllegalArgumentException("The action no longer matches the preview.");
        start = playback.start().find(call.target()).orElseThrow();
        current = playback.stateAt(time).find(call.target()).orElseThrow();
        for (int index = 0; index < playback.calls().size(); index++) {
            var other = playback.calls().get(index);
            if (index != callIndex && other.target().equals(call.target()))
                writable.removeIf(track -> ActionKeyframes.track(other.action(), track).isPresent());
        }
        this.definition = original; preview = playback;
    }
    public SceneObjectState current() { return current; }
    public String target() { return call.target(); }
    public boolean canMove() { return writable.contains("x") || writable.contains("y"); }
    // Resizing about the opposite handle also writes the object's position.
    public boolean canResize() { return writable.containsAll(List.of("x", "y", "scale")) && current.scale() > 0; }
    public JsonObject definition() { return definition.deepCopy(); }
    public ScenePlayback preview() { return preview; }
    public ScenePlayback originalPlayback() { return playback; }
    public boolean changed() { return !original.equals(definition); }

    public boolean update(float x, float y, float scale) {
        if (!Float.isFinite(x - start.x()) || !Float.isFinite(y - start.y()) || !Float.isFinite(scale - start.scale()) || scale <= 0) return false;
        JsonObject next = original.deepCopy();
        if (writable.contains("x") && x != current.x()) put(next, "x", x - start.x());
        if (writable.contains("y") && y != current.y()) put(next, "y", y - start.y());
        if (canResize() && scale != current.scale()) put(next, "scale", scale - start.scale());
        var calls = new ArrayList<>(playback.calls());
        calls.set(callIndex, new ResolvedActionCall(call.target(), call.delayMs(), decode(next)));
        var sampled = new ScenePlayback(playback.token(), playback.start(), playback.end(), calls,
                playback.totalDurationMs(), playback.blockingDurationMs());
        preview = new ScenePlayback(sampled.token(), sampled.start(), sampled.stateAt(sampled.totalDurationMs()), calls,
                sampled.totalDurationMs(), sampled.blockingDurationMs());
        definition = next;
        return true;
    }
    private void put(JsonObject action, String track, float value) {
        float at = call.action().easing().apply((time - call.delayMs()) / (float)call.action().durationMs());
        var frames = action.has(track) ? action.getAsJsonArray(track) : new JsonArray();
        int index = 0;
        for (var entry : frames) {
            var frame = entry.getAsJsonObject(); float position = frame.get("at").getAsFloat();
            if (position == at) { frame.add("value", number(value)); return; }
            if (position < at) index++;
        }
        // Match the timeline's millisecond markers without introducing near-duplicate keyframes.
        for (int i = 0; i < frames.size(); i++) {
            var frame = frames.get(i).getAsJsonObject();
            float position = frame.get("at").getAsFloat();
            if (ActionKeyframes.timeAtProgress(call.action(), position, call.delayMs()) == time) {
                frames.remove(i); if (i < index) index--;
                frame.add("at", number(at)); frame.add("value", number(value));
                DialogueDraft.insert(frames, index, frame); return;
            }
        }
        var frame = new JsonObject(); frame.add("at", number(at)); frame.add("value", number(value));
        DialogueDraft.insert(frames, index, frame); action.add(track, frames);
        // NumericTrack supplies the implicit (0, 0) start when the first keyframe is later than zero.
    }
    private static JsonPrimitive number(float value) { return new JsonPrimitive(new BigDecimal(Float.toString(value))); }
    private static SceneAction decode(JsonObject json) { return SceneAction.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(); }
}
