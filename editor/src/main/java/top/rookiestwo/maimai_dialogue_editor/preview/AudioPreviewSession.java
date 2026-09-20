package top.rookiestwo.maimai_dialogue_editor.preview;

import top.rookiestwo.maimai_dialogue_editor.project.ProjectBlob;
import java.util.Objects;

/** One selected sound's transport, independent of Views and the audio device. All calls belong to the UI thread. */
public final class AudioPreviewSession {
    public enum State { STOPPED, LOADING, PLAYING, PAUSED, FINISHED, FAILED }
    public interface Listener {
        void ready(double duration);
        void progress(double seconds, boolean finished);
        void failed(String message);
    }
    public interface Voice { void pause(boolean paused); void stop(); void poll(); }
    @FunctionalInterface public interface Backend { Voice start(ProjectBlob blob, Listener listener); }
    private final Backend backend;
    private final Runnable changed;
    private Object selection;
    private ProjectBlob blob;
    private Voice voice;
    private long revision;
    private State state = State.STOPPED;
    private double position, duration;
    private String error = "";

    public AudioPreviewSession(Backend backend, Runnable changed) { this.backend = backend; this.changed = changed; }
    public State state() { return state; }
    public double position() { return position; }
    public double duration() { return duration; }
    public String error() { return error; }
    public boolean available() { return blob != null; }

    public void select(Object identity, ProjectBlob next) {
        if (Objects.equals(selection, identity) && blob == next) return;
        stop(); selection = identity; blob = next; duration = 0;
    }
    public void play() {
        if (blob == null || state == State.LOADING || state == State.PLAYING) return;
        if (state == State.PAUSED && voice != null) {
            voice.pause(false); state = State.PLAYING; changed.run(); return;
        }
        stop();
        long expected = ++revision;
        state = State.LOADING; changed.run();
        try {
            Voice started = backend.start(blob, new Listener() {
                private boolean current() { return expected == revision; }
                public void ready(double seconds) {
                    if (!current()) return;
                    duration = Math.max(0, seconds); state = State.PLAYING; changed.run();
                }
                public void progress(double seconds, boolean finished) {
                    if (!current()) return;
                    position = Math.max(0, duration > 0 ? Math.min(duration, seconds) : seconds);
                    if (finished) {
                        if (duration > 0) position = duration;
                        state = State.FINISHED;
                        ++revision;
                        if (voice != null) { voice.stop(); voice = null; }
                    }
                    changed.run();
                }
                public void failed(String message) {
                    if (!current()) return;
                    error = message; state = State.FAILED; ++revision;
                    if (voice != null) { voice.stop(); voice = null; }
                    changed.run();
                }
            });
            if (expected != revision) started.stop(); else voice = started;
        } catch (RuntimeException failure) {
            ++revision; state = State.FAILED; error = String.valueOf(failure.getMessage()); changed.run();
        }
    }
    public void pause() {
        if (state != State.PLAYING || voice == null) return;
        voice.pause(true); state = State.PAUSED; changed.run();
    }
    public void stop() {
        ++revision;
        if (voice != null) { voice.stop(); voice = null; }
        state = State.STOPPED; position = 0; error = "";
    }
    public void stopAndNotify() { stop(); changed.run(); }
    public void tick() { if (voice != null) voice.poll(); }
}
