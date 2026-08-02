package top.rookiestwo.maimai_dialogue.client;

import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.animation.ValueAnimator;

import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * A finite logical timeline whose wall-clock rate can change while running.
 */
final class PlaybackTimeline {
    private static final float NORMAL_RATE = 1.0F;

    private ValueAnimator animator;
    private IntConsumer frameConsumer;
    private Runnable finished;
    private int totalDurationMs;
    private int logicalElapsedMs;
    private int segmentStartElapsedMs;
    private int segmentLogicalDurationMs;
    private long segmentWallDurationMs;
    private float playbackRate = NORMAL_RATE;
    private boolean completed;

    void start(
            int durationMs,
            IntConsumer nextFrameConsumer,
            Runnable nextFinished
    ) {
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative.");
        }
        cancel();
        totalDurationMs = durationMs;
        logicalElapsedMs = 0;
        frameConsumer = Objects.requireNonNull(
                nextFrameConsumer,
                "nextFrameConsumer"
        );
        finished = Objects.requireNonNull(nextFinished, "nextFinished");
        completed = false;
        if (durationMs == 0) {
            dispatchFrame(0);
            complete();
            return;
        }
        startRemainingSegment();
    }

    void setPlaybackRate(float nextRate) {
        if (!Float.isFinite(nextRate) || nextRate <= 0.0F) {
            throw new IllegalArgumentException(
                    "playbackRate must be finite and positive."
            );
        }
        if (Math.abs(playbackRate - nextRate) < 0.0001F) {
            return;
        }

        ValueAnimator current = animator;
        if (current != null) {
            updateFrom(current);
            cancelAnimator();
        }
        playbackRate = nextRate;
        if (!completed
                && frameConsumer != null
                && logicalElapsedMs < totalDurationMs) {
            startRemainingSegment();
        }
    }

    void cancel() {
        cancelAnimator();
        frameConsumer = null;
        finished = null;
        totalDurationMs = 0;
        logicalElapsedMs = 0;
        segmentStartElapsedMs = 0;
        segmentLogicalDurationMs = 0;
        segmentWallDurationMs = 0L;
        completed = false;
    }

    private void startRemainingSegment() {
        segmentStartElapsedMs = logicalElapsedMs;
        segmentLogicalDurationMs = totalDurationMs - segmentStartElapsedMs;
        segmentWallDurationMs = wallDurationMs(
                segmentLogicalDurationMs,
                playbackRate
        );
        ValueAnimator next = ValueAnimator.ofFloat(0.0F, 1.0F);
        next.setDuration(segmentWallDurationMs);
        next.setInterpolator(TimeInterpolator.LINEAR);
        next.addUpdateListener(valueAnimator -> {
            if (animator != next) {
                return;
            }
            long wallElapsed = Math.min(
                    segmentWallDurationMs,
                    valueAnimator.getCurrentPlayTime()
            );
            int elapsed = segmentStartElapsedMs + Math.round(
                    segmentLogicalDurationMs
                            * (wallElapsed / (float) segmentWallDurationMs)
            );
            dispatchFrame(Math.min(totalDurationMs, elapsed));
            if (logicalElapsedMs >= totalDurationMs) {
                complete();
            }
        });
        animator = next;
        next.start();
    }

    private void updateFrom(ValueAnimator current) {
        long wallElapsed = Math.min(
                segmentWallDurationMs,
                current.getCurrentPlayTime()
        );
        int elapsed = segmentStartElapsedMs + Math.round(
                segmentLogicalDurationMs
                        * (wallElapsed / (float) segmentWallDurationMs)
        );
        dispatchFrame(Math.min(totalDurationMs, elapsed));
        if (logicalElapsedMs >= totalDurationMs) {
            complete();
        }
    }

    private void dispatchFrame(int elapsedMs) {
        logicalElapsedMs = Math.max(logicalElapsedMs, elapsedMs);
        IntConsumer consumer = frameConsumer;
        if (consumer != null) {
            consumer.accept(logicalElapsedMs);
        }
    }

    private void complete() {
        if (completed) {
            return;
        }
        completed = true;
        animator = null;
        Runnable callback = finished;
        finished = null;
        if (callback != null) {
            callback.run();
        }
    }

    private void cancelAnimator() {
        ValueAnimator current = animator;
        animator = null;
        if (current != null) {
            current.cancel();
        }
    }

    static long wallDurationMs(int logicalDurationMs, float playbackRate) {
        if (logicalDurationMs < 0
                || !Float.isFinite(playbackRate)
                || playbackRate <= 0.0F) {
            throw new IllegalArgumentException(
                    "Duration must be non-negative and rate positive."
            );
        }
        if (logicalDurationMs == 0) {
            return 0L;
        }
        return Math.max(
                1L,
                (long) Math.ceil(logicalDurationMs / playbackRate)
        );
    }
}
