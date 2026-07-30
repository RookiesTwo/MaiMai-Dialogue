package top.rookiestwo.maimai_dialogue.client.scene;

import top.rookiestwo.maimai_dialogue.presentation.action.PresentationAction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ScenePlayback(
        long token,
        SceneState start,
        SceneState end,
        List<ResolvedActionCall> calls,
        int totalDurationMs,
        int blockingDurationMs
) {
    public ScenePlayback {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(calls, "calls");
        calls = List.copyOf(calls);
    }

    public SceneState stateAt(int elapsedMs) {
        SceneState state = start;
        for (ResolvedActionCall call : calls) {
            if (call.target().equals("dialogue")) {
                state = applyDialogue(state, call, elapsedMs);
                continue;
            }
            if (call.target().equals("background")) {
                state = applyBackground(state, call, elapsedMs);
                continue;
            }
            SceneObjectState initial = start.find(call.target())
                    .orElseThrow();
            SceneObjectState current = state.find(call.target())
                    .orElseThrow();
            PresentationAction action = call.action();
            float fraction;
            if (action.durationMs() == 0) {
                fraction = elapsedMs >= call.delayMs() ? 1.0F : 0.0F;
            } else {
                fraction = (elapsedMs - call.delayMs())
                        / (float) action.durationMs();
                fraction = Math.max(0.0F, Math.min(1.0F, fraction));
            }
            final float actionFraction = fraction;
            float eased = action.easing().apply(fraction);

            float x = action.x()
                    .map(track -> initial.x() + track.valueAt(eased))
                    .orElse(current.x());
            float y = action.y()
                    .map(track -> initial.y() + track.valueAt(eased))
                    .orElse(current.y());
            float scale = action.scale()
                    .map(track -> initial.scale() + track.valueAt(eased))
                    .orElse(current.scale());
            float opacity = action.opacity()
                    .map(track -> initial.opacity() + track.valueAt(eased))
                    .orElse(current.opacity());
            String variant = action.variant()
                    .filter(change -> actionFraction >= change.at())
                    .map(change -> change.variant())
                    .orElse(current.variant());
            boolean visible = action.visible()
                    .filter(change -> actionFraction >= change.at())
                    .map(change -> change.visible())
                    .orElse(current.visible());

            state = state.with(
                    call.target(),
                    current.withAnimated(
                            x,
                            y,
                            scale,
                            opacity,
                            variant,
                            visible
                    )
            );
        }
        return state;
    }

    public Optional<BackgroundCrossfade> backgroundCrossfadeAt(
            int elapsedMs
    ) {
        for (ResolvedActionCall call : calls) {
            if (!call.target().equals("background")
                    || call.action().variant().isEmpty()) {
                continue;
            }
            PresentationAction action = call.action();
            int fadeStart = call.delayMs() + (int) Math.ceil(
                    action.durationMs()
                            * action.variant().orElseThrow().at()
            );
            int fadeEnd = call.endTimeMs();
            if (elapsedMs < fadeStart
                    || elapsedMs >= fadeEnd) {
                continue;
            }

            SceneBackgroundState from =
                    start.background().orElseThrow();
            SceneBackgroundState to = from.withVariant(
                    action.variant().orElseThrow().variant()
            );
            float linearProgress = (elapsedMs - fadeStart)
                    / (float) (fadeEnd - fadeStart);
            return Optional.of(new BackgroundCrossfade(
                    from,
                    to,
                    action.easing().apply(linearProgress)
            ));
        }
        return Optional.empty();
    }

    private SceneState applyDialogue(
            SceneState state,
            ResolvedActionCall call,
            int elapsedMs
    ) {
        PresentationAction action = call.action();
        float fraction;
        if (action.durationMs() == 0) {
            fraction = elapsedMs >= call.delayMs() ? 1.0F : 0.0F;
        } else {
            fraction = Math.clamp(
                    (elapsedMs - call.delayMs())
                            / (float) action.durationMs(),
                    0.0F,
                    1.0F
            );
        }
        float opacity = action.opacity()
                .map(track -> start.dialogueOpacity()
                        + track.valueAt(action.easing().apply(fraction)))
                .orElse(state.dialogueOpacity());
        return state.withDialogueOpacity(opacity);
    }

    private SceneState applyBackground(
            SceneState state,
            ResolvedActionCall call,
            int elapsedMs
    ) {
        SceneBackgroundState initial = start.background().orElseThrow();
        SceneBackgroundState current = state.background().orElseThrow();
        PresentationAction action = call.action();
        float fraction;
        if (action.durationMs() == 0) {
            fraction = elapsedMs >= call.delayMs() ? 1.0F : 0.0F;
        } else {
            fraction = (elapsedMs - call.delayMs())
                    / (float) action.durationMs();
            fraction = Math.clamp(fraction, 0.0F, 1.0F);
        }
        float actionFraction = fraction;
        String variant = action.variant()
                .filter(change -> actionFraction >= change.at())
                .map(change -> change.variant())
                .orElse(current.variant());
        if (variant.equals(current.variant())) {
            return state;
        }
        return state.withBackground(initial.withVariant(variant));
    }
}
