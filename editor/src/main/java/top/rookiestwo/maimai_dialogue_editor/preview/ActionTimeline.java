package top.rookiestwo.maimai_dialogue_editor.preview;

import top.rookiestwo.maimai_dialogue.client.scene.ScenePlayback;
import top.rookiestwo.maimai_dialogue.presentation.action.*;
import java.util.*;

/** Editor transport and detached timeline metadata. Seeking never dispatches runtime effects or edits a draft. */
public final class ActionTimeline {
    public record Marker(String property, int timeMs) {}
    public record Lane(int callIndex, String target, int startMs, int endMs, List<Marker> markers) {
        public Lane { markers = List.copyOf(markers); }
    }
    private Object owner;
    private ScenePlayback playback;
    private List<Lane> lanes = List.of();
    private int position;
    private boolean manual;
    public ScenePlayback playback() { return playback; }
    public List<Lane> lanes() { return lanes; }
    public int duration() { return playback == null ? 0 : playback.totalDurationMs(); }
    public int position() { return position; }
    public boolean manual() { return manual; }
    public top.rookiestwo.maimai_dialogue.client.scene.ResolvedActionCall call(int callIndex) {
        if (playback != null && callIndex >= 0) for (int i = 0; i < lanes.size(); i++)
            if (lanes.get(i).callIndex() == callIndex) return playback.calls().get(i);
        return null;
    }
    public void clear() { owner = null; playback = null; lanes = List.of(); position = 0; manual = false; }
    public void clear(Object expected) { if (owner == expected) clear(); }
    public void bind(Object owner, ScenePlayback next, int authoredCount, boolean playing) {
        if (this.owner == owner && playback == next) return;
        this.owner = owner; playback = next; position = 0; manual = !playing;
        lanes = next == null ? List.of() : describe(next, authoredCount);
    }
    public boolean follow(Object owner, long token, int time) {
        if (this.owner != owner || playback == null || playback.token() != token || manual) return false;
        position = Math.clamp(time, 0, duration()); return true;
    }
    public boolean seek(ScenePlayback expected, int time) {
        if (expected == null || expected != playback) return false;
        manual = true; position = Math.clamp(time, 0, duration()); return true;
    }
    public static List<Lane> describe(ScenePlayback playback, int authoredCount) {
        var result = new ArrayList<Lane>();
        // The runtime prepends its default first-step dialogue fade; it is visible but not an authored call.
        int automatic = Math.max(0, playback.calls().size() - authoredCount);
        for (int i = 0; i < playback.calls().size(); i++) {
            var call = playback.calls().get(i); var action = call.action(); var markers = new ArrayList<Marker>();
            numeric(markers, "x", action.x(), action, call.delayMs());
            numeric(markers, "y", action.y(), action, call.delayMs());
            numeric(markers, "scale", action.scale(), action, call.delayMs());
            numeric(markers, "opacity", action.opacity(), action, call.delayMs());
            action.variant().ifPresent(value -> markers.add(new Marker("variant", call.delayMs() + (int)Math.ceil(action.durationMs() * value.at()))));
            action.visible().ifPresent(value -> markers.add(new Marker("visible", call.delayMs() + (int)Math.ceil(action.durationMs() * value.at()))));
            if (action.sound().isPresent()) markers.add(new Marker("sound", call.delayMs()));
            if (action.bgm().isPresent()) markers.add(new Marker("bgm", call.delayMs()));
            markers.sort(Comparator.comparingInt(Marker::timeMs).thenComparing(Marker::property));
            result.add(new Lane(i < automatic ? -1 : i - automatic, call.target(), call.delayMs(), call.endTimeMs(), markers));
        }
        return List.copyOf(result);
    }
    private static void numeric(List<Marker> result, String property, Optional<NumericTrack> track, SceneAction action, int delay) {
        track.ifPresent(value -> value.keyframes().forEach(frame -> result.add(new Marker(property,
                delay + Math.round(action.durationMs() * inverse(action.easing(), frame.at()))))));
    }
    /** Numeric keyframes sample eased action progress; the ruler uses actual elapsed milliseconds. */
    static float inverse(ActionEasing easing, float progress) {
        return top.rookiestwo.maimai_dialogue_editor.document.ActionKeyframes.linearProgress(easing, progress);
    }
}
