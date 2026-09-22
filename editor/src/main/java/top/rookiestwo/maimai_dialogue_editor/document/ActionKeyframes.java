package top.rookiestwo.maimai_dialogue_editor.document;

import com.google.gson.*;
import top.rookiestwo.maimai_dialogue.presentation.action.*;
import java.util.Optional;

/** Converts wall-clock cursor time to the runtime's eased track coordinate without changing the curve. */
public final class ActionKeyframes {
    public record Insertion(int index, float at, float value, String error) {
        public boolean valid() { return error.isEmpty(); }
    }
    private ActionKeyframes() {}
    public static Optional<NumericTrack> track(SceneAction action, String name) {
        return switch (name) {
            case "x" -> action.x(); case "y" -> action.y(); case "scale" -> action.scale(); case "opacity" -> action.opacity();
            default -> Optional.empty();
        };
    }
    public static Insertion plan(SceneAction action, String name, int timeMs, int delayMs) {
        var track = track(action, name);
        if (track.isEmpty()) return invalid("no_track");
        if (action.durationMs() <= 0) return invalid("zero_duration");
        long local = (long)timeMs - delayMs;
        if (local < 0 || local > action.durationMs()) return invalid("outside");
        float at = action.easing().apply(local / (float)action.durationMs());
        var frames = track.orElseThrow().keyframes(); int index = 0;
        for (var frame : frames) {
            // A marker is shown at millisecond resolution; do not create a second marker in the same millisecond.
            if (frame.at() == at || timeAtProgress(action, frame.at(), delayMs) == timeMs) return invalid("duplicate");
            if (frame.at() < at) index++;
        }
        return new Insertion(index, at, track.orElseThrow().valueAt(at), "");
    }
    private static Insertion invalid(String error) { return new Insertion(-1, 0, 0, error); }
    public static void insert(JsonObject action, String track, Insertion insertion) {
        if (!insertion.valid()) throw new IllegalArgumentException(insertion.error());
        var frame = new JsonObject();
        // Store the runtime float's decimal representation so saving/reopening retains identical draft numbers.
        frame.add("at", new JsonPrimitive(new java.math.BigDecimal(Float.toString(insertion.at()))));
        frame.add("value", new JsonPrimitive(new java.math.BigDecimal(Float.toString(insertion.value()))));
        DialogueDraft.insert(action.getAsJsonArray(track), insertion.index(), frame);
    }
    public static int timeAtProgress(SceneAction action, float progress, int delayMs) {
        return delayMs + Math.round(action.durationMs() * linearProgress(action.easing(), progress));
    }
    public static float linearProgress(ActionEasing easing, float progress) {
        if (progress <= 0) return 0;
        if (progress >= 1) return 1;
        float low = 0, high = 1;
        for (int i = 0; i < 24; i++) {
            float mid = (low + high) / 2;
            if (easing.apply(mid) < progress) low = mid; else high = mid;
        }
        return (low + high) / 2;
    }
}
